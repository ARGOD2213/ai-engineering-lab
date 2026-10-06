package com.aiengineeringlab.api.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Describes the embedding model this service is configured to use.
 * <p>
 * Developer note: {@code dimensions} is a property of the <em>model</em>, not of the database.
 * all-MiniLM-L6-v2 always returns 384 numbers, text-embedding-3-small returns 1536. The pgvector
 * column ({@code vector(384)}) must be declared with exactly the same size, which is why this
 * value is also used to configure Spring AI's PgVectorStore and is verified at startup.
 *
 * @param provider human-readable provider name (for example "transformers-onnx" or "openai")
 * @param model model identifier
 * @param dimensions expected vector size produced by the model
 * @param verifyDimensionsOnStartup embed a probe text at startup and fail fast on a mismatch
 */
@Validated
@ConfigurationProperties("lab.embedding")
public record EmbeddingProperties(
        @NotBlank String provider,
        @NotBlank String model,
        @Positive int dimensions,
        boolean verifyDimensionsOnStartup) {
}
