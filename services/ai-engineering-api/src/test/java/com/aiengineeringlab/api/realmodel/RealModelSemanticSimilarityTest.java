package com.aiengineeringlab.api.realmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.aiengineeringlab.api.service.TextSimilarity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.transformers.TransformersEmbeddingModel;

/**
 * The "same meaning vs. different meaning" experiment against the REAL all-MiniLM-L6-v2 model.
 * No Spring context, no database, no LLM - just the model.
 * <p>
 * Run with: {@code ./mvnw test -Preal-model}. The first run downloads the ~90 MB ONNX model.
 */
@Tag("real-model")
class RealModelSemanticSimilarityTest {

    private static TransformersEmbeddingModel model;

    @BeforeAll
    static void loadModel() throws Exception {
        model = new TransformersEmbeddingModel();
        model.setResourceCacheDirectory(
                Path.of(System.getProperty("user.home"), ".cache", "ai-engineering-lab", "onnx").toString());
        model.afterPropertiesSet();
    }

    @AfterAll
    static void closeModel() throws Exception {
        model.close();
    }

    @Test
    void sameMeaningScoresFarHigherThanDifferentMeaning() {
        String a = "Employees receive 12 casual leave days every year.";
        String b = "Workers are entitled to twelve days of casual leave annually.";
        String c = "Spring Boot provides dependency injection.";
        List<float[]> vectors = model.embed(List.of(a, b, c));

        double ab = TextSimilarity.cosineSimilarity(vectors.get(0), vectors.get(1));
        double ac = TextSimilarity.cosineSimilarity(vectors.get(0), vectors.get(2));
        double bc = TextSimilarity.cosineSimilarity(vectors.get(1), vectors.get(2));
        System.out.printf("A-B %.4f | A-C %.4f | B-C %.4f | keyword overlap A-B %.4f%n", ab, ac, bc,
                TextSimilarity.keywordOverlap(a, b));

        assertThat(vectors).allSatisfy(vector -> assertThat(vector).hasSize(384));
        assertThat(ab).isGreaterThan(0.8);
        assertThat(ac).isLessThan(0.3);
        assertThat(bc).isLessThan(0.3);
        // The paraphrase shares few words, yet the vectors are close: that is the point of embeddings.
        assertThat(TextSimilarity.keywordOverlap(a, b)).isLessThan(0.3);
    }

    @Test
    void textBeyondTheModelsTokenLimitIsSilentlyIgnored() {
        String longText = "Employees receive twelve casual leave days every calendar year and unused days do not carry forward. "
                .repeat(8);
        String tail = " Completely unrelated appendix: the cafeteria serves pizza on Fridays.";

        double longWithTail = TextSimilarity.cosineSimilarity(model.embed(longText), model.embed(longText + tail));
        double shortWithTail = TextSimilarity.cosineSimilarity(model.embed("Employees receive casual leave."),
                model.embed("Employees receive casual leave." + tail));
        System.out.printf("long vs long+tail %.4f | short vs short+tail %.4f%n", longWithTail, shortWithTail);

        // The tokenizer truncates at 128 tokens: the appended sentence never reaches the model.
        assertThat(longWithTail).isGreaterThan(0.9999);
        assertThat(shortWithTail).isLessThan(0.95);
    }

    @Test
    void vectorsAreNotUnitLength() {
        // Spring AI's ONNX pipeline uses mean pooling without L2 normalisation. Cosine distance ignores
        // vector length, so it is the right metric here; inner product would not be.
        float[] vector = model.embed("Spring Boot provides dependency injection.");
        double norm = 0;
        for (float value : vector) {
            norm += value * value;
        }
        System.out.printf("L2 norm %.4f%n", Math.sqrt(norm));
        assertThat(Math.sqrt(norm)).isNotCloseTo(1.0, org.assertj.core.api.Assertions.within(0.05));
    }
}
