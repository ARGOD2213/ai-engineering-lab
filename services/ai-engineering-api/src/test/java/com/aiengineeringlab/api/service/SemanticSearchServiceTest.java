package com.aiengineeringlab.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.SearchResult;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import com.aiengineeringlab.api.exception.InvalidRequestException;
import com.aiengineeringlab.api.exception.VectorStoreUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.dao.QueryTimeoutException;

class SemanticSearchServiceTest {

    private final VectorStore vectorStore = mock(VectorStore.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final SemanticSearchService service = new SemanticSearchService(vectorStore,
            new EmbeddingProperties("mock", "mock-model", 384, false), meterRegistry);

    @Test
    void buildsTheSearchRequestAndMapsResultsInRankOrder() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                Document.builder().id("hr-emergency-leave").text("Emergency leave: 5 days")
                        .metadata(Map.of("department", "HR", "distance", 0.2f)).score(0.8).build(),
                Document.builder().id("hr-casual-leave").text("Casual leave: 12 days")
                        .metadata(Map.of("department", "HR", "distance", 0.4f)).score(0.6).build()));

        SearchResult result = service.search("How many emergency leave days?", 2, 0.5, "department == 'HR'");

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        SearchRequest sent = captor.getValue();
        assertThat(sent.getQuery()).isEqualTo("How many emergency leave days?");
        assertThat(sent.getTopK()).isEqualTo(2);
        assertThat(sent.getSimilarityThreshold()).isEqualTo(0.5);
        assertThat(sent.hasFilterExpression()).isTrue();

        assertThat(result.resultCount()).isEqualTo(2);
        assertThat(result.results().getFirst().rank()).isEqualTo(1);
        assertThat(result.results().getFirst().id()).isEqualTo("hr-emergency-leave");
        assertThat(result.results().getFirst().score()).isEqualTo(0.8);
        assertThat(result.results().getFirst().distance()).isCloseTo(0.2, org.assertj.core.api.Assertions.within(1e-6));
        // "distance" is moved out of metadata into its own field.
        assertThat(result.results().getFirst().metadata()).containsOnlyKeys("department");
        assertThat(meterRegistry.get("lab.search.results").summary().totalAmount()).isEqualTo(2.0);
    }

    @Test
    void emptyResultIsNotAnError() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        SearchResult result = service.search("anything", 5, 0.9, null);

        assertThat(result.resultCount()).isZero();
        assertThat(result.results()).isEmpty();
    }

    @Test
    void invalidFilterExpressionIsABadRequestAndNeverReachesTheStore() {
        assertThatThrownBy(() -> service.search("q", 5, 0.0, "department ==== HR"))
                .isInstanceOf(InvalidRequestException.class);
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void translatesDatabaseFailures() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new QueryTimeoutException("timeout"));

        assertThatThrownBy(() -> service.search("q", 5, 0.0, null)).isInstanceOf(VectorStoreUnavailableException.class);
    }

    @Test
    void translatesEmbeddingFailures() {
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenThrow(new IllegalStateException("provider down"));

        assertThatThrownBy(() -> service.search("q", 5, 0.0, null)).isInstanceOf(EmbeddingProviderException.class);
    }
}
