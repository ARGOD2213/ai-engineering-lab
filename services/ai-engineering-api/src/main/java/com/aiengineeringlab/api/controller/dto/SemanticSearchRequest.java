package com.aiengineeringlab.api.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * @param query natural-language question; it is embedded with the same model as the documents
 * @param topK how many nearest documents to return (default 5)
 * @param similarityThreshold drop results whose cosine similarity is below this value. The default 0.0 is
 *     Spring AI's "accept all", but PgVectorStore implements it as {@code distance < 1}, so documents with a
 *     NEGATIVE cosine similarity (pointing away from the query) are still excluded.
 * @param filterExpression optional metadata filter in Spring AI's portable syntax, e.g. {@code department == 'HR'}
 */
public record SemanticSearchRequest(
        @NotBlank(message = "query must not be blank")
        @Size(max = ApiLimits.MAX_QUERY_LENGTH, message = "query must be at most {max} characters")
        String query,

        @Min(value = 1, message = "topK must be at least {value}")
        @Max(value = ApiLimits.MAX_TOP_K, message = "topK must be at most {value}")
        @Nullable Integer topK,

        @DecimalMin(value = "0.0", message = "similarityThreshold must be between 0 and 1")
        @DecimalMax(value = "1.0", message = "similarityThreshold must be between 0 and 1")
        @Nullable Double similarityThreshold,

        @Size(max = 500, message = "filterExpression must be at most {max} characters")
        @Nullable String filterExpression) {

    public static final int DEFAULT_TOP_K = 5;

    public int topKOrDefault() {
        return topK != null ? topK : DEFAULT_TOP_K;
    }

    public double similarityThresholdOrDefault() {
        return similarityThreshold != null ? similarityThreshold : 0.0;
    }
}
