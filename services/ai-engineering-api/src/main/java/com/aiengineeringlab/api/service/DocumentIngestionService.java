package com.aiengineeringlab.api.service;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.BatchIngestionResult;
import com.aiengineeringlab.api.domain.NewDocument;
import com.aiengineeringlab.api.domain.StoredDocument;
import com.aiengineeringlab.api.exception.DocumentNotFoundException;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import com.aiengineeringlab.api.exception.InvalidRequestException;
import com.aiengineeringlab.api.exception.VectorStoreUnavailableException;
import com.aiengineeringlab.api.repository.DocumentRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/**
 * Stores text + metadata + vector in pgvector.
 * <p>
 * Developer note: notice that we never call the embedding model here. Spring AI's
 * {@link VectorStore#add} receives plain text, calls the configured {@code EmbeddingModel} itself,
 * and then writes text, metadata and vector in one INSERT. pgvector never sees "meaning" - it only
 * stores arrays of floats and measures distances between them.
 */
@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final DocumentRepository documentRepository;
    private final EmbeddingProperties embeddingProperties;
    private final Counter ingestedDocuments;

    public DocumentIngestionService(VectorStore vectorStore, DocumentRepository documentRepository,
            EmbeddingProperties embeddingProperties, MeterRegistry meterRegistry) {
        this.vectorStore = vectorStore;
        this.documentRepository = documentRepository;
        this.embeddingProperties = embeddingProperties;
        this.ingestedDocuments = Counter.builder("lab.documents.ingested")
                .description("Documents embedded and stored in the vector store")
                .register(meterRegistry);
    }

    public StoredDocument ingest(NewDocument newDocument) {
        Document document = toSpringAiDocument(newDocument);
        store(List.of(document));
        // Read back from PostgreSQL: proves the row exists and returns the database's created_at and vector size.
        return findById(document.getId(), false);
    }

    public BatchIngestionResult ingestAll(List<NewDocument> newDocuments) {
        List<Document> documents = newDocuments.stream().map(this::toSpringAiDocument).toList();
        store(documents);
        return new BatchIngestionResult(documents.size(), documents.stream().map(Document::getId).toList(),
                embeddingProperties.model(), embeddingProperties.dimensions());
    }

    public StoredDocument findById(String id, boolean includeEmbedding) {
        try {
            return documentRepository.findById(id, includeEmbedding).orElseThrow(() -> new DocumentNotFoundException(id));
        }
        catch (DataAccessException ex) {
            throw new VectorStoreUnavailableException("Could not read document", ex);
        }
    }

    private void store(List<Document> documents) {
        long start = System.nanoTime();
        try {
            vectorStore.add(documents);
        }
        catch (DataAccessException ex) {
            throw new VectorStoreUnavailableException("Could not write documents to the vector store", ex);
        }
        catch (RuntimeException ex) {
            // VectorStore.add() first embeds, then writes. Database errors surface as DataAccessException
            // (handled above), so anything else here comes from the embedding step.
            throw new EmbeddingProviderException("Could not embed documents", ex);
        }
        ingestedDocuments.increment(documents.size());
        long totalChars = documents.stream().mapToLong(document -> Objects.requireNonNull(document.getText()).length()).sum();
        log.info("Stored documents: count={} totalChars={} model={} latencyMs={}",
                documents.size(), totalChars, embeddingProperties.model(), (System.nanoTime() - start) / 1_000_000);
    }

    private Document toSpringAiDocument(NewDocument newDocument) {
        Map<String, Object> metadata = newDocument.metadata();
        if (metadata.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getValue() == null)) {
            throw new InvalidRequestException("metadata keys and values must not be null");
        }
        String id = newDocument.id() != null ? newDocument.id() : UUID.randomUUID().toString();
        return Document.builder().id(id).text(newDocument.text()).metadata(metadata).build();
    }
}
