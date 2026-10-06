package com.aiengineeringlab.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.BatchIngestionResult;
import com.aiengineeringlab.api.domain.NewDocument;
import com.aiengineeringlab.api.domain.StoredDocument;
import com.aiengineeringlab.api.exception.DocumentNotFoundException;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import com.aiengineeringlab.api.exception.InvalidRequestException;
import com.aiengineeringlab.api.exception.VectorStoreUnavailableException;
import com.aiengineeringlab.api.repository.DocumentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.DataAccessResourceFailureException;

class DocumentIngestionServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final DocumentRepository repository = mock(DocumentRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final DocumentIngestionService service = new DocumentIngestionService(vectorStore, repository,
            new EmbeddingProperties("mock", "mock-model", 384, false), meterRegistry);

    @Test
    @SuppressWarnings("unchecked")
    void passesTextAndMetadataToTheVectorStoreAndReadsTheRowBack() {
        when(repository.findById(eq("hr-1"), anyBoolean())).thenReturn(Optional.of(stored("hr-1")));

        StoredDocument result = service.ingest(new NewDocument("hr-1",
                "Employees can take up to 5 days of emergency leave.", Map.of("department", "HR")));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        Document sent = captor.getValue().getFirst();
        assertThat(sent.getId()).isEqualTo("hr-1");
        assertThat(sent.getText()).isEqualTo("Employees can take up to 5 days of emergency leave.");
        assertThat(sent.getMetadata()).containsEntry("department", "HR");
        assertThat(result.embeddingDimensions()).isEqualTo(384);
        assertThat(meterRegistry.get("lab.documents.ingested").counter().count()).isEqualTo(1.0);
    }

    @Test
    @SuppressWarnings("unchecked")
    void generatesAnIdWhenNoneIsGiven() {
        when(repository.findById(anyString(), anyBoolean())).thenAnswer(invocation -> Optional.of(stored(invocation.getArgument(0))));

        StoredDocument result = service.ingest(new NewDocument(null, "text", Map.of()));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue().getFirst().getId()).isNotBlank().isEqualTo(result.id());
    }

    @Test
    @SuppressWarnings("unchecked")
    void storesABatchWithOneVectorStoreCall() {
        BatchIngestionResult result = service.ingestAll(List.of(
                new NewDocument("a", "first", Map.of()),
                new NewDocument("b", "second", Map.of("department", "HR"))));

        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(result.stored()).isEqualTo(2);
        assertThat(result.ids()).containsExactly("a", "b");
        assertThat(result.embeddingDimensions()).isEqualTo(384);
    }

    @Test
    void rejectsNullMetadataValues() {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("department", null);

        assertThatThrownBy(() -> service.ingest(new NewDocument("x", "text", metadata)))
                .isInstanceOf(InvalidRequestException.class);
        verify(vectorStore, never()).add(any());
    }

    @Test
    void translatesDatabaseFailures() {
        doThrow(new DataAccessResourceFailureException("connection refused")).when(vectorStore).add(any());

        assertThatThrownBy(() -> service.ingest(new NewDocument("x", "text", Map.of())))
                .isInstanceOf(VectorStoreUnavailableException.class);
    }

    @Test
    void translatesEmbeddingFailures() {
        doThrow(new IllegalStateException("model not loaded")).when(vectorStore).add(any());

        assertThatThrownBy(() -> service.ingest(new NewDocument("x", "text", Map.of())))
                .isInstanceOf(EmbeddingProviderException.class);
    }

    @Test
    void unknownIdIsNotFound() {
        when(repository.findById("missing", false)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById("missing", false)).isInstanceOf(DocumentNotFoundException.class);
    }

    private static StoredDocument stored(String id) {
        return new StoredDocument(id, "text", Map.of(), 384, "mock-model", OffsetDateTime.now(), null);
    }
}
