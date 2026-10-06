package com.aiengineeringlab.api.service;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.EmbeddingResult;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Turns text into vectors using Spring AI's {@link EmbeddingModel} abstraction.
 * <p>
 * Developer note: this class does not know which model is behind {@code EmbeddingModel}. The local
 * ONNX model and OpenAI are swapped by configuration only. What does change between models is the
 * vector size and the "meaning space" - vectors from two different models must never be compared.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingProperties properties;

    public EmbeddingService(EmbeddingModel embeddingModel, EmbeddingProperties properties) {
        this.embeddingModel = embeddingModel;
        this.properties = properties;
    }

    public EmbeddingResult embed(String text) {
        long start = System.nanoTime();
        float[] embedding = callProvider(() -> embeddingModel.embed(text));
        // Log sizes and timings only - never the text itself, it may contain personal data.
        log.info("Generated embedding: chars={} dimensions={} latencyMs={}",
                text.length(), embedding.length, elapsedMs(start));
        return new EmbeddingResult(text, properties.model(), embedding.length, embedding);
    }

    private <T> T callProvider(Supplier<T> call) {
        try {
            return call.get();
        }
        catch (RuntimeException ex) {
            throw new EmbeddingProviderException("Embedding model '%s' failed".formatted(properties.model()), ex);
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
