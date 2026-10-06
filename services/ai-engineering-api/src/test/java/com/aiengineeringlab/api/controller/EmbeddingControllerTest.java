package com.aiengineeringlab.api.controller;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.domain.EmbeddingResult;
import com.aiengineeringlab.api.domain.SimilarityReport;
import com.aiengineeringlab.api.exception.EmbeddingProviderException;
import com.aiengineeringlab.api.service.EmbeddingService;
import com.aiengineeringlab.api.support.TestMetricsConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmbeddingController.class)
@Import(TestMetricsConfiguration.class)
class EmbeddingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmbeddingService embeddingService;

    @Test
    void returnsTextDimensionsAndVector() throws Exception {
        when(embeddingService.embed("Employees are entitled to 12 casual leave days per year."))
                .thenReturn(new EmbeddingResult("Employees are entitled to 12 casual leave days per year.", "mock-model",
                        3, new float[] {0.1f, -0.2f, 0.3f}));

        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"text": "Employees are entitled to 12 casual leave days per year."}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("Employees are entitled to 12 casual leave days per year."))
                .andExpect(jsonPath("$.dimensions").value(3))
                .andExpect(jsonPath("$.embedding.length()").value(3))
                .andExpect(jsonPath("$.model").value("mock-model"));
    }

    @Test
    void blankTextIsRejectedBeforeAnyModelCall() throws Exception {
        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.text").value("text must not be blank"));
        verifyNoInteractions(embeddingService);
    }

    @Test
    void tooLongTextIsRejected() throws Exception {
        String text = "a".repeat(8_001);
        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": \"" + text + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("text must be at most 8000 characters"));
    }

    @Test
    void malformedJsonIsABadRequest() throws Exception {
        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerOutageIsA503WithoutInternalDetails() throws Exception {
        when(embeddingService.embed(anyString()))
                .thenThrow(new EmbeddingProviderException("Embedding model 'x' failed", new RuntimeException("secret detail")));

        mockMvc.perform(post("/api/embeddings").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Embedding provider unavailable"))
                .andExpect(jsonPath("$.detail").value("The embedding model is currently unavailable. Please retry later."));
    }

    @Test
    void similarityNeedsAtLeastTwoTexts() throws Exception {
        mockMvc.perform(post("/api/embeddings/similarity").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texts\": [\"only one\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.texts").exists());
    }

    @Test
    void similarityReturnsPairs() throws Exception {
        when(embeddingService.compare(anyList())).thenReturn(new SimilarityReport("mock-model", 3,
                List.of(new SimilarityReport.LabeledText("A", "a"), new SimilarityReport.LabeledText("B", "b")),
                List.of(new SimilarityReport.SimilarityPair("A", "B", 0.89, 0.25))));

        mockMvc.perform(post("/api/embeddings/similarity").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texts\": [\"a\", \"b\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairs[0].cosineSimilarity").value(0.89))
                .andExpect(jsonPath("$.pairs[0].keywordOverlap").value(0.25));
    }
}
