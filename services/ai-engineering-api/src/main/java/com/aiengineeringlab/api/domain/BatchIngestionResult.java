package com.aiengineeringlab.api.domain;

import java.util.List;

/** Outcome of storing several documents with a single, batched embedding call. */
public record BatchIngestionResult(int stored, List<String> ids, String embeddingModel, int embeddingDimensions) {
}
