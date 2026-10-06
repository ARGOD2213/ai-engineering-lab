package com.aiengineeringlab.api.domain;

/**
 * The output of an embedding model for one text.
 *
 * @param text the original input text
 * @param model the model that produced the vector (vectors from different models are not comparable)
 * @param dimensions the actual length of the vector returned by the model
 * @param embedding the vector itself
 */
public record EmbeddingResult(String text, String model, int dimensions, float[] embedding) {
}
