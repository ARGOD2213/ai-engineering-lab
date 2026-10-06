package com.aiengineeringlab.api.domain;

import java.util.Map;

/**
 * One semantic search result.
 *
 * @param rank 1 = closest match
 * @param score cosine similarity (1 - distance); higher is more similar
 * @param distance cosine distance as computed by pgvector's {@code <=>} operator; lower is more similar
 */
public record SearchHit(int rank, String id, String text, Map<String, Object> metadata, double score, double distance) {
}
