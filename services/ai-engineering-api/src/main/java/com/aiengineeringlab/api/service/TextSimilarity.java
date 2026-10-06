package com.aiengineeringlab.api.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Small, dependency-free similarity functions used by the experiments.
 * <p>
 * Developer note: pgvector computes cosine distance in SQL ({@code embedding <=> query}). Having the
 * same maths in plain Java makes it obvious that there is no magic: cosine similarity is a dot
 * product divided by the vector lengths, and {@code distance = 1 - similarity}.
 */
public final class TextSimilarity {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "by", "can", "do", "does", "for", "from", "how", "i", "in",
            "is", "it", "its", "me", "my", "of", "on", "or", "our", "the", "their", "they", "this", "to", "was",
            "we", "what", "when", "which", "with", "you", "your");

    private TextSimilarity() {
    }

    /** Cosine similarity in [-1, 1]. 1 means the vectors point in exactly the same direction. */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vectors must have the same dimensions to be compared: %d vs %d".formatted(a.length, b.length));
        }
        double dot = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /**
     * Jaccard overlap of the meaningful words of two texts, in [0, 1]. A deliberately naive stand-in
     * for keyword search: it only sees shared spelling, never shared meaning.
     */
    public static double keywordOverlap(String a, String b) {
        Set<String> wordsA = words(a);
        Set<String> wordsB = words(b);
        if (wordsA.isEmpty() && wordsB.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        return (double) intersection.size() / union.size();
    }

    static Set<String> words(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isBlank())
                .filter(word -> !STOP_WORDS.contains(word))
                .collect(Collectors.toSet());
    }
}
