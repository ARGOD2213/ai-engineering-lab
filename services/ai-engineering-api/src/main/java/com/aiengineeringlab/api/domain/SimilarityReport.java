package com.aiengineeringlab.api.domain;

import java.util.List;

/**
 * Pairwise comparison of several texts, used by the "same meaning vs. different meaning" experiment.
 *
 * @param model embedding model used for every text
 * @param dimensions vector size
 * @param texts the inputs, labelled A, B, C, ...
 * @param pairs every pair of inputs, most similar first
 */
public record SimilarityReport(String model, int dimensions, List<LabeledText> texts, List<SimilarityPair> pairs) {

    public record LabeledText(String label, String text) {
    }

    /**
     * @param cosineSimilarity semantic closeness of the two embeddings (1 = same direction)
     * @param keywordOverlap naive lexical baseline: Jaccard overlap of the (non stop-word) words
     */
    public record SimilarityPair(String first, String second, double cosineSimilarity, double keywordOverlap) {
    }
}
