package com.aiengineeringlab.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class TextSimilarityTest {

    @Test
    void identicalDirectionHasSimilarityOne() {
        assertThat(TextSimilarity.cosineSimilarity(new float[] {1, 2, 3}, new float[] {2, 4, 6}))
                .isCloseTo(1.0, within(1e-9));
    }

    @Test
    void orthogonalVectorsHaveSimilarityZero() {
        assertThat(TextSimilarity.cosineSimilarity(new float[] {1, 0}, new float[] {0, 1})).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void oppositeVectorsHaveSimilarityMinusOne() {
        assertThat(TextSimilarity.cosineSimilarity(new float[] {1, 1}, new float[] {-1, -1}))
                .isCloseTo(-1.0, within(1e-9));
    }

    @Test
    void vectorsOfDifferentDimensionsCannotBeCompared() {
        assertThatThrownBy(() -> TextSimilarity.cosineSimilarity(new float[384], new float[1536]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("384 vs 1536");
    }

    @Test
    void zeroVectorHasNoSimilarity() {
        assertThat(TextSimilarity.cosineSimilarity(new float[] {0, 0}, new float[] {1, 1})).isZero();
    }

    @Test
    void keywordOverlapSeesSpellingNotMeaning() {
        // Same meaning, different words: low overlap.
        double paraphrase = TextSimilarity.keywordOverlap(
                "Employees receive 12 casual leave days every year.",
                "Workers are entitled to twelve days of casual leave annually.");
        // Different meaning, shared word "leave": overlap is not zero.
        double sameWordDifferentMeaning = TextSimilarity.keywordOverlap(
                "Customers can leave a review.", "Employees get casual leave.");

        assertThat(paraphrase).isBetween(0.2, 0.3);
        assertThat(sameWordDifferentMeaning).isGreaterThan(0.0);
        assertThat(TextSimilarity.keywordOverlap("Spring beans", "spring BEANS")).isEqualTo(1.0);
    }

    @Test
    void stopWordsAreIgnored() {
        assertThat(TextSimilarity.words("What is the default port of a Spring Boot app?"))
                .containsExactlyInAnyOrder("default", "port", "spring", "boot", "app");
    }
}
