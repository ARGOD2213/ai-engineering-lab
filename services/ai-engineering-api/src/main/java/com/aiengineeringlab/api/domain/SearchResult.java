package com.aiengineeringlab.api.domain;

import java.util.List;
import org.jspecify.annotations.Nullable;

/** The question, the parameters used, and the top-K documents nearest to it. */
public record SearchResult(
        String query,
        int topK,
        double similarityThreshold,
        @Nullable String filterExpression,
        String embeddingModel,
        int resultCount,
        List<SearchHit> results) {
}
