package com.aiengineeringlab.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.EmbeddingResult;
import com.aiengineeringlab.api.domain.SimilarityReport;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class EmbeddingServiceTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
    private final EmbeddingService service = new EmbeddingService(embeddingModel,
            new EmbeddingProperties("mock", "mock-model", 4, false));

    @Test
    void reportsTheActualDimensionsReturnedByTheModel() {
        when(embeddingModel.embed("hello")).thenReturn(new float[] {0.1f, 0.2f, 0.3f});

        EmbeddingResult result = service.embed("hello");

        // The configured value is 4, but the model returned 3 numbers: the response must say 3.
        assertThat(result.dimensions()).isEqualTo(3);
        assertThat(result.embedding()).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.model()).isEqualTo("mock-model");
    }

    @Test
    void wrapsProviderFailures() {
        when(embeddingModel.embed("hello")).thenThrow(new RuntimeException("401 invalid api key sk-***"));

        assertThatThrownBy(() -> service.embed("hello"))
                .isInstanceOf(EmbeddingProviderException.class)
                .hasMessage("Embedding model 'mock-model' failed")
                .hasMessageNotContaining("sk-");
    }

    @Test
    void comparesEveryPairWithOneBatchedCallMostSimilarFirst() {
        when(embeddingModel.embed(anyList())).thenReturn(List.of(
                new float[] {1, 0, 0},
                new float[] {0.9f, 0.1f, 0},
                new float[] {0, 0, 1}));

        SimilarityReport report = service.compare(List.of(
                "Employees receive 12 casual leave days every year.",
                "Workers are entitled to twelve days of casual leave annually.",
                "Spring Boot provides dependency injection."));

        verify(embeddingModel).embed(anyList());
        assertThat(report.dimensions()).isEqualTo(3);
        assertThat(report.texts()).extracting(SimilarityReport.LabeledText::label).containsExactly("A", "B", "C");
        assertThat(report.pairs()).hasSize(3);
        SimilarityReport.SimilarityPair best = report.pairs().getFirst();
        assertThat(best.first()).isEqualTo("A");
        assertThat(best.second()).isEqualTo("B");
        assertThat(best.cosineSimilarity()).isGreaterThan(0.99);
        assertThat(report.pairs().getLast().cosineSimilarity()).isZero();
    }
}
