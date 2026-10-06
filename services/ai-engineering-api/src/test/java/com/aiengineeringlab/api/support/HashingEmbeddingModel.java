package com.aiengineeringlab.api.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

/**
 * Deterministic stand-in for a real embedding model, used by tests that must not download a model
 * or call a paid API.
 * <p>
 * It uses "feature hashing": every word is hashed to one of the vector positions. Texts that share
 * words therefore get similar vectors. That is a keyword model, NOT a semantic one - good enough to
 * test plumbing (storage, ordering, filters, dimensions), useless for testing meaning. Semantic
 * behaviour is tested separately with the real model (tag "real-model").
 */
public class HashingEmbeddingModel implements EmbeddingModel {

    private final int dimensions;
    private final AtomicInteger calls = new AtomicInteger();

    public HashingEmbeddingModel(int dimensions) {
        this.dimensions = dimensions;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        calls.incrementAndGet();
        List<Embedding> embeddings = new ArrayList<>();
        List<String> texts = request.getInstructions();
        for (int i = 0; i < texts.size(); i++) {
            embeddings.add(new Embedding(embedText(texts.get(i)), i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embedText(document.getText());
    }

    @Override
    public int dimensions() {
        return dimensions;
    }

    public int calls() {
        return calls.get();
    }

    public float[] embedText(String text) {
        float[] vector = new float[dimensions];
        for (String word : text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.isBlank()) {
                continue;
            }
            CRC32 crc = new CRC32();
            crc.update(word.getBytes(StandardCharsets.UTF_8));
            vector[(int) (crc.getValue() % dimensions)] += 1f;
        }
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        if (norm > 0) {
            float length = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] /= length;
            }
        }
        return vector;
    }
}
