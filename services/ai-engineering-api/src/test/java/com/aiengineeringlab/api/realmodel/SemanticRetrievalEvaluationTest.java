package com.aiengineeringlab.api.realmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiengineeringlab.api.domain.NewDocument;
import com.aiengineeringlab.api.domain.SearchHit;
import com.aiengineeringlab.api.service.DocumentIngestionService;
import com.aiengineeringlab.api.service.SemanticSearchService;
import com.aiengineeringlab.api.service.TextSimilarity;
import com.aiengineeringlab.api.support.PgVectorTestcontainersConfiguration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Retrieval-quality experiment: real model + real pgvector + the shared dataset in
 * {@code 02-vector-databases/dataset}. Prints a ranking table and writes
 * {@code target/semantic-retrieval-report.md}.
 * <p>
 * This is the experiment we will re-run against Chroma, Qdrant and Redis: same documents, same
 * queries, same model - only the vector store changes.
 * <p>
 * Run with: {@code ./mvnw test -Preal-model} (needs Docker; downloads the model on first run).
 */
@Tag("real-model")
@SpringBootTest
@ActiveProfiles("local")
@Import(PgVectorTestcontainersConfiguration.class)
@Testcontainers(disabledWithoutDocker = true)
class SemanticRetrievalEvaluationTest {

    private static final Path DATASET = Path.of("../../02-vector-databases/dataset");

    @Autowired
    private DocumentIngestionService ingestionService;

    @Autowired
    private SemanticSearchService searchService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void vectorSearchFindsTheRightDocumentsAndBeatsKeywordMatchingOnParaphrases() throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        JsonNode documents = json.readTree(Files.readString(DATASET.resolve("documents.json"))).get("documents");
        JsonNode queries = json.readTree(Files.readString(DATASET.resolve("queries.json"))).get("queries");

        jdbcClient.sql("TRUNCATE documents_minilm_l6_v2").update();
        Map<String, String> texts = new LinkedHashMap<>();
        List<NewDocument> newDocuments = new ArrayList<>();
        for (JsonNode document : documents) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            document.get("metadata").properties().forEach(entry -> metadata.put(entry.getKey(), entry.getValue().asString()));
            newDocuments.add(new NewDocument(document.get("id").asString(), document.get("text").asString(), metadata));
            texts.put(document.get("id").asString(), document.get("text").asString());
        }
        ingestionService.ingestAll(newDocuments);

        int vectorHit1 = 0;
        int vectorHit3 = 0;
        int keywordHit1 = 0;
        int paraphraseQueries = 0;
        int vectorParaphraseHit1 = 0;
        int keywordParaphraseHit1 = 0;
        StringBuilder report = new StringBuilder("""
                | Query | Challenge | Expected | Vector #1 (score) | Vector rank of expected | Keyword #1 |
                |---|---|---|---|---|---|
                """);

        for (JsonNode query : queries) {
            String question = query.get("query").asString();
            Set<String> relevant = relevantIds(query);

            List<SearchHit> hits = searchService.search(question, 3, 0.0, null).results();
            int rank = rankOfRelevant(hits, relevant);
            String keywordTop = texts.keySet().stream()
                    .max(Comparator.comparingDouble(id -> TextSimilarity.keywordOverlap(question, texts.get(id))))
                    .orElseThrow();

            boolean vectorTop1 = rank == 1;
            boolean keywordTop1 = relevant.contains(keywordTop);
            vectorHit1 += vectorTop1 ? 1 : 0;
            vectorHit3 += rank > 0 ? 1 : 0;
            keywordHit1 += keywordTop1 ? 1 : 0;
            if ("PARAPHRASE".equals(query.get("challenge").asString())) {
                paraphraseQueries++;
                vectorParaphraseHit1 += vectorTop1 ? 1 : 0;
                keywordParaphraseHit1 += keywordTop1 ? 1 : 0;
            }

            report.append("| %s | %s | %s | %s (%.3f) | %s | %s %s |%n".formatted(question,
                    query.get("challenge").asString(), query.get("expected").asString(), hits.getFirst().id(),
                    hits.getFirst().score(), rank > 0 ? String.valueOf(rank) : ">3", keywordTop,
                    keywordTop1 ? "(ok)" : "(wrong)"));
        }

        int total = queries.size();
        report.append("%nVector hit@1: %d/%d, hit@3: %d/%d. Keyword baseline hit@1: %d/%d.%n".formatted(
                vectorHit1, total, vectorHit3, total, keywordHit1, total));
        report.append("Paraphrase queries hit@1 - vector: %d/%d, keyword: %d/%d.%n".formatted(
                vectorParaphraseHit1, paraphraseQueries, keywordParaphraseHit1, paraphraseQueries));
        System.out.println(report);
        Files.writeString(Path.of("target", "semantic-retrieval-report.md"), report);

        assertThat(vectorHit3).as("expected document within the top 3").isGreaterThanOrEqualTo((int) Math.ceil(total * 0.85));
        assertThat(vectorHit1).as("expected document ranked first").isGreaterThanOrEqualTo((int) Math.ceil(total * 0.7));
        assertThat(vectorParaphraseHit1).as("vector search beats exact-word matching on paraphrases")
                .isGreaterThan(keywordParaphraseHit1);
    }

    private static Set<String> relevantIds(JsonNode query) {
        Set<String> relevant = new java.util.HashSet<>();
        relevant.add(query.get("expected").asString());
        if (query.has("alsoRelevant")) {
            query.get("alsoRelevant").forEach(node -> relevant.add(node.asString()));
        }
        return relevant;
    }

    private static int rankOfRelevant(List<SearchHit> hits, Set<String> relevant) {
        for (SearchHit hit : hits) {
            if (relevant.contains(hit.id())) {
                return hit.rank();
            }
        }
        return 0;
    }
}
