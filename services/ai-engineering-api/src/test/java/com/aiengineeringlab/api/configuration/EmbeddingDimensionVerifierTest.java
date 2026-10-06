package com.aiengineeringlab.api.configuration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;

class EmbeddingDimensionVerifierTest {

    private final EmbeddingModel embeddingModel = mock(EmbeddingModel.class);

    @Test
    void startsWhenTheModelMatchesTheConfiguredDimensions() {
        when(embeddingModel.embed(anyString())).thenReturn(new float[384]);

        assertThatCode(() -> verifier(384, true).afterPropertiesSet()).doesNotThrowAnyException();
    }

    @Test
    void refusesToStartWhenTheModelProducesADifferentSize() {
        // e.g. switching to a 1536-dimension model while the table is still vector(384)
        when(embeddingModel.embed(anyString())).thenReturn(new float[1536]);

        assertThatThrownBy(() -> verifier(384, true).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("produced 1536 dimensions")
                .hasMessageContaining("configured for 384");
    }

    @Test
    void canBeDisabled() {
        verifier(384, false).afterPropertiesSet();

        verifyNoInteractions(embeddingModel);
    }

    private EmbeddingDimensionVerifier verifier(int dimensions, boolean enabled) {
        return new EmbeddingDimensionVerifier(embeddingModel,
                new EmbeddingProperties("mock", "mock-model", dimensions, enabled));
    }
}
