package com.aiengineeringlab.api.service;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.SearchHit;
import com.aiengineeringlab.api.domain.SearchResult;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import com.aiengineeringlab.api.exception.InvalidRequestException;
import com.aiengineeringlab.api.exception.VectorStoreUnavailableException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentMetadata;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Pure vector search: question → query vector → nearest documents. No LLM is involved.
 * <p>
 * Developer note: the query is embedded with the SAME model as the documents (VectorStore does it
 * for us). Then pgvector runs {@code ORDER BY embedding <=> :queryVector LIMIT :topK}. The
 * result is "the K closest texts in meaning", not "the answer" - turning them into an answer is
 * what an LLM will do in the RAG milestone.
 */
@Service
public class SemanticSearchService {

    private static final Logger log = LoggerFactory.getLogger(SemanticSearchService.class);

    private final VectorStore vectorStore;
    private final EmbeddingProperties embeddingProperties;
    private final DistributionSummary resultCount;

    public SemanticSearchService(VectorStore vectorStore, EmbeddingProperties embeddingProperties,
            MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.embeddingProperties = embeddingProperties;
        this.resultCount = DistributionSummary.builder("lab.search.results")
                .description("Number of documents returned per semantic search")
                .register(meterRegistry);
    }

    public SearchResult search(String query, int topK, double similarityThreshold, @Nullable String filterExpression) {
        SearchRequest request = buildRequest(query, topK, similarityThreshold, filterExpression);

        long start = System.nanoTime();
        List<Document> documents;
        try {
            documents = vectorStore.similaritySearch(request);
        }
        catch (DataAccessException ex) {
            throw new VectorStoreUnavailableException("Similarity search failed", ex);
        }
        catch (RuntimeException ex) {
            // Database errors are DataAccessExceptions; the remaining failure point is embedding the query.
            throw new EmbeddingProviderException("Could not embed the search query", ex);
        }
        long latencyMs = (System.nanoTime() - start) / 1_000_000;

        List<SearchHit> hits = new ArrayList<>();
        for (int i = 0; i < documents.size(); i++) {
            hits.add(toHit(i + 1, documents.get(i)));
        }
        resultCount.record(hits.size());
        // The query itself is not logged: user questions can contain personal data.
        log.info("Semantic search: queryChars={} topK={} threshold={} filtered={} results={} topScore={} latencyMs={}",
                query.length(), topK, similarityThreshold, request.hasFilterExpression(), hits.size(),
                hits.isEmpty() ? "n/a" : "%.4f".formatted(hits.getFirst().score()), latencyMs);

        return new SearchResult(query, topK, similarityThreshold, filterExpression, embeddingProperties.model(),
                hits.size(), hits);
    }

    private SearchRequest buildRequest(String query, int topK, double similarityThreshold,
            @Nullable String filterExpression) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold);
        if (StringUtils.hasText(filterExpression)) {
            try {
                // Spring AI's portable filter language. PgVectorStore translates it to a jsonpath
                // predicate on the metadata column; Qdrant/Redis/Chroma stores translate the same
                // expression to their own native filter syntax.
                builder.filterExpression(filterExpression);
            }
            catch (RuntimeException ex) {
                throw new InvalidRequestException(
                        "Invalid filterExpression. Example of a valid one: department == 'HR' && documentType == 'POLICY'",
                        ex);
            }
        }
        return builder.build();
    }

    private static SearchHit toHit(int rank, Document document) {
        Map<String, Object> metadata = new HashMap<>(document.getMetadata());
        // PgVectorStore adds the raw distance to the metadata; expose it as its own field instead.
        Object distanceValue = metadata.remove(DocumentMetadata.DISTANCE.value());
        double score = document.getScore() != null ? document.getScore() : 0.0;
        double distance = distanceValue instanceof Number number ? number.doubleValue() : 1.0 - score;
        return new SearchHit(rank, document.getId(), document.getText(), metadata, round(score), round(distance));
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }
}
