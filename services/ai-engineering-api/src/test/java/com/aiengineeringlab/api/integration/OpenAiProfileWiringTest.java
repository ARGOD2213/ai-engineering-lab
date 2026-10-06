package com.aiengineeringlab.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.support.HashingEmbeddingModel;
import com.aiengineeringlab.api.support.PgVectorTestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Verifies the opt-in "openai" profile end-to-end WITHOUT calling OpenAI: a tiny in-process HTTP server
 * speaks the OpenAI embeddings wire format, and the real Spring AI OpenAI client is pointed at it.
 * <p>
 * Proves: the profile selects the OpenAI EmbeddingModel (not the local ONNX one), sends the configured model
 * and an API key, produces 1536-d vectors, uses its own table, and batches ingestion into one call.
 * What it cannot prove: that OpenAI's real servers accept your key. Try that manually with your own key.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("openai")
@Import(PgVectorTestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class OpenAiProfileWiringTest {

    private static final int OPENAI_DIMENSIONS = 1536;
    private static final HashingEmbeddingModel STUB_VECTORS = new HashingEmbeddingModel(OPENAI_DIMENSIONS);
    private static final List<JsonNode> RECEIVED_REQUESTS = new CopyOnWriteArrayList<>();
    private static final List<String> RECEIVED_AUTH_HEADERS = new CopyOnWriteArrayList<>();
    private static HttpServer stubOpenAi;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private JdbcClient jdbcClient;

    @BeforeAll
    static void startStub() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        stubOpenAi = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubOpenAi.createContext("/v1/embeddings", exchange -> {
            JsonNode request = json.readTree(exchange.getRequestBody().readAllBytes());
            RECEIVED_REQUESTS.add(request);
            RECEIVED_AUTH_HEADERS.add(exchange.getRequestHeaders().getFirst("Authorization"));

            List<String> inputs = new ArrayList<>();
            if (request.get("input").isArray()) {
                request.get("input").forEach(node -> inputs.add(node.asString()));
            }
            else {
                inputs.add(request.get("input").asString());
            }
            boolean base64 = request.has("encoding_format") && "base64".equals(request.get("encoding_format").asString());

            StringBuilder data = new StringBuilder();
            for (int i = 0; i < inputs.size(); i++) {
                float[] vector = STUB_VECTORS.embedText(inputs.get(i));
                String encoded = base64 ? "\"" + toBase64(vector) + "\"" : toJsonArray(vector);
                data.append(i > 0 ? "," : "")
                        .append("{\"object\":\"embedding\",\"index\":").append(i).append(",\"embedding\":").append(encoded).append('}');
            }
            byte[] body = ("{\"object\":\"list\",\"data\":[" + data + "],\"model\":\"text-embedding-3-small\","
                    + "\"usage\":{\"prompt_tokens\":7,\"total_tokens\":7}}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        });
        stubOpenAi.start();
    }

    @AfterAll
    static void stopStub() {
        stubOpenAi.stop(0);
    }

    @DynamicPropertySource
    static void pointOpenAiClientAtStub(DynamicPropertyRegistry registry) {
        registry.add("spring.ai.openai.base-url", () -> "http://127.0.0.1:" + stubOpenAi.getAddress().getPort() + "/v1");
        // Always a dummy key in tests, even if a real one is present in .env.
        registry.add("spring.ai.openai.api-key", () -> "sk-test-dummy-key");
    }

    @Test
    void openAiProfileUsesTheOpenAiModelItsOwnTableAndBatching() throws Exception {
        assertThat(embeddingModel.getClass().getSimpleName()).isEqualTo("OpenAiEmbeddingModel");

        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dimensions").value(OPENAI_DIMENSIONS))
                .andExpect(jsonPath("$.model").value("text-embedding-3-small"));

        int requestsBefore = RECEIVED_REQUESTS.size();
        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON).content("""
                        {"documents": [
                          {"id": "hr-emergency-leave", "text": "Employees can take up to 5 days of emergency leave."},
                          {"id": "spring-beans", "text": "A Spring bean is managed by the IoC container."}
                        ]}
                        """))
                .andExpect(status().isCreated());
        assertThat(RECEIVED_REQUESTS.size() - requestsBefore).as("one batched embedding call").isEqualTo(1);

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"How many days of emergency leave?\", \"topK\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("hr-emergency-leave"));

        assertThat(jdbcClient.sql("SELECT vector_dims(embedding) FROM documents_openai_text_embedding_3_small LIMIT 1")
                .query(Integer.class).single()).isEqualTo(OPENAI_DIMENSIONS);
        assertThat(jdbcClient.sql("SELECT count(*) FROM documents_minilm_l6_v2").query(Long.class).single())
                .as("the MiniLM table is untouched").isZero();

        assertThat(RECEIVED_REQUESTS).allSatisfy(request ->
                assertThat(request.get("model").asString()).isEqualTo("text-embedding-3-small"));
        assertThat(RECEIVED_AUTH_HEADERS).allSatisfy(header -> assertThat(header).isEqualTo("Bearer sk-test-dummy-key"));

        mockMvc.perform(get("/actuator/info"))
                .andExpect(jsonPath("$.embedding.provider").value("openai"))
                .andExpect(jsonPath("$.vectorStore.table").value("public.documents_openai_text_embedding_3_small"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("sk-test"));
    }

    private static String toBase64(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return Base64.getEncoder().encodeToString(buffer.array());
    }

    private static String toJsonArray(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(i > 0 ? "," : "").append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
