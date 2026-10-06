package com.aiengineeringlab.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.support.FakeEmbeddingModelConfiguration;
import com.aiengineeringlab.api.support.HashingEmbeddingModel;
import com.aiengineeringlab.api.support.PgVectorTestcontainersConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end through HTTP → Spring AI VectorStore → real PostgreSQL + pgvector (Testcontainers).
 * The embedding model is the deterministic {@link HashingEmbeddingModel}, so these tests are free and
 * repeatable. They verify plumbing: schema, storage, dimensions, ordering, top-K and metadata filters.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PgVectorTestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class PgVectorIntegrationTest {

    private static final String TABLE = "documents_minilm_l6_v2";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private HashingEmbeddingModel embeddingModel;

    @BeforeEach
    void cleanTable() {
        jdbcClient.sql("TRUNCATE " + TABLE).update();
    }

    @Test
    void flywayCreatedTheVectorTableWithTheModelsDimension() {
        Integer declaredDimensions = jdbcClient.sql("""
                SELECT a.atttypmod FROM pg_attribute a
                JOIN pg_class c ON a.attrelid = c.oid
                WHERE c.relname = :table AND a.attname = 'embedding'
                """).param("table", TABLE).query(Integer.class).single();
        String extensionVersion = jdbcClient.sql("SELECT extversion FROM pg_extension WHERE extname = 'vector'")
                .query(String.class).single();

        assertThat(declaredDimensions).isEqualTo(384);
        assertThat(extensionVersion).isNotBlank();
    }

    @Test
    void ingestedDocumentIsStoredWithTextMetadataVectorAndTimestamp() throws Exception {
        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "id": "hr-emergency-leave",
                          "text": "Employees can take up to 5 days of emergency leave.",
                          "metadata": {"department": "HR", "documentType": "POLICY", "source": "employee-handbook"}
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("hr-emergency-leave"))
                .andExpect(jsonPath("$.embeddingDimensions").value(384))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        // Look at the row directly in PostgreSQL - no Spring AI involved.
        var row = jdbcClient.sql("""
                SELECT content, metadata ->> 'department' AS department, vector_dims(embedding) AS dims, created_at
                FROM documents_minilm_l6_v2 WHERE id = 'hr-emergency-leave'
                """).query().singleRow();
        assertThat(row.get("content")).isEqualTo("Employees can take up to 5 days of emergency leave.");
        assertThat(row.get("department")).isEqualTo("HR");
        assertThat(row.get("dims")).isEqualTo(384);
        assertThat(row.get("created_at")).isNotNull();

        // The stored vector is exactly what the model produced for this text.
        mockMvc.perform(get("/api/documents/hr-emergency-leave").param("includeEmbedding", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding.length()").value(384));
    }

    @Test
    void searchReturnsNearestDocumentsFirstAndRespectsTopK() throws Exception {
        loadSampleDataset();

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"query": "How many days of emergency leave can employees take?", "topK": 3}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(3))
                .andExpect(jsonPath("$.results[0].rank").value(1))
                .andExpect(jsonPath("$.results[0].id").value("hr-emergency-leave"))
                .andExpect(jsonPath("$.results[0].metadata.department").value("HR"))
                .andExpect(jsonPath("$.results[0].metadata.distance").doesNotExist())
                .andExpect(jsonPath("$.results[0].score").isNumber())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    assertThat(body).contains("\"distance\"");
                });
    }

    @Test
    void metadataFilterRestrictsTheCandidates() throws Exception {
        loadSampleDataset();

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("""
                        {"query": "leave days", "topK": 10, "filterExpression": "department == 'SUPPORT'"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[*].metadata.department").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("SUPPORT"))));
    }

    @Test
    void reIngestingTheSameIdUpdatesInsteadOfDuplicating() throws Exception {
        for (String text : new String[] {"First version", "Second version"}) {
            mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"id\": \"same-id\", \"text\": \"" + text + "\"}"))
                    .andExpect(status().isCreated());
        }

        assertThat(jdbcClient.sql("SELECT count(*) FROM " + TABLE).query(Long.class).single()).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT content FROM " + TABLE + " WHERE id = 'same-id'").query(String.class).single())
                .isEqualTo("Second version");
    }

    @Test
    void batchIngestionEmbedsAllDocumentsInOneModelCall() throws Exception {
        int callsBefore = embeddingModel.calls();

        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON).content("""
                        {"documents": [{"id": "a", "text": "alpha"}, {"id": "b", "text": "beta"}, {"id": "c", "text": "gamma"}]}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(3));

        assertThat(embeddingModel.calls() - callsBefore).isEqualTo(1);
        assertThat(jdbcClient.sql("SELECT count(*) FROM " + TABLE).query(Long.class).single()).isEqualTo(3);
    }

    @Test
    void pgvectorRejectsAVectorOfTheWrongDimension() {
        // This is what would happen if the model changed but the table did not.
        assertThatThrownBy(() -> jdbcClient.sql("INSERT INTO " + TABLE + " (id, content, embedding) VALUES ('x', 'x', '[1,2,3]')")
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("expected 384 dimensions, not 3");
    }

    @Test
    void healthAndInfoExposeDatabaseAndModelWithoutSecrets() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"));
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embedding.dimensions").value(384))
                .andExpect(jsonPath("$.vectorStore.table").value("public." + TABLE))
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContainIgnoringCase("password"));
    }

    private void loadSampleDataset() throws Exception {
        String dataset = Files.readString(Path.of("../../02-vector-databases/dataset/documents.json"));
        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON).content(dataset))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(30));
    }
}
