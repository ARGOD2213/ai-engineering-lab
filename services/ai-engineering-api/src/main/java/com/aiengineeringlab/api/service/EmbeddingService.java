package com.aiengineeringlab.api.service;

import com.aiengineeringlab.api.configuration.EmbeddingProperties;
import com.aiengineeringlab.api.domain.EmbeddingResult;
import com.aiengineeringlab.api.domain.SimilarityReport;
import com.aiengineeringlab.api.domain.SimilarityReport.LabeledText;
import com.aiengineeringlab.api.domain.SimilarityReport.SimilarityPair;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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

    /**
     * Embeds all texts in one batched call and compares every pair. No LLM is involved: the
     * similarity comes purely from the geometry of the vectors.
     */
    public SimilarityReport compare(List<String> texts) {
        long start = System.nanoTime();
        List<float[]> embeddings = callProvider(() -> embeddingModel.embed(texts));

        List<LabeledText> labeled = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            labeled.add(new LabeledText(label(i), texts.get(i)));
        }

        List<SimilarityPair> pairs = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            for (int j = i + 1; j < texts.size(); j++) {
                pairs.add(new SimilarityPair(label(i), label(j),
                        round(TextSimilarity.cosineSimilarity(embeddings.get(i), embeddings.get(j))),
                        round(TextSimilarity.keywordOverlap(texts.get(i), texts.get(j)))));
            }
        }
        pairs.sort(Comparator.comparingDouble(SimilarityPair::cosineSimilarity).reversed());

        int dimensions = embeddings.getFirst().length;
        log.info("Compared texts: count={} dimensions={} latencyMs={}", texts.size(), dimensions, elapsedMs(start));
        return new SimilarityReport(properties.model(), dimensions, labeled, pairs);
    }

    private <T> T callProvider(Supplier<T> call) {
        try {
            return call.get();
        }
        catch (RuntimeException ex) {
            throw new EmbeddingProviderException("Embedding model '%s' failed".formatted(properties.model()), ex);
        }
    }

    private static String label(int index) {
        return index < 26 ? String.valueOf((char) ('A' + index)) : "T" + (index + 1);
    }

    private static double round(double value) {
        return Math.round(value * 10_000d) / 10_000d;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
