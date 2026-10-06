package com.aiengineeringlab.api.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.domain.SearchHit;
import com.aiengineeringlab.api.domain.SearchResult;
import com.aiengineeringlab.api.exception.InvalidRequestException;
import com.aiengineeringlab.api.service.SemanticSearchService;
import com.aiengineeringlab.api.support.TestMetricsConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SearchController.class)
@Import(TestMetricsConfiguration.class)
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SemanticSearchService searchService;

    @Test
    void appliesDefaultsAndReturnsScoredResults() throws Exception {
        when(searchService.search(anyString(), anyInt(), anyDouble(), any())).thenReturn(new SearchResult(
                "How many emergency leave days can an employee take?", 5, 0.0, null, "mock-model", 1,
                List.of(new SearchHit(1, "hr-emergency-leave", "Employees can take up to 5 days of emergency leave.",
                        Map.of("department", "HR"), 0.81, 0.19))));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"How many emergency leave days can an employee take?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results[0].id").value("hr-emergency-leave"))
                .andExpect(jsonPath("$.results[0].score").value(0.81))
                .andExpect(jsonPath("$.results[0].metadata.department").value("HR"));

        verify(searchService).search(eq("How many emergency leave days can an employee take?"), eq(5), eq(0.0), isNull());
    }

    @Test
    void rejectsOutOfRangeTopKAndThreshold() throws Exception {
        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"q\", \"topK\": 0, \"similarityThreshold\": 1.5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.topK").value("topK must be at least 1"))
                .andExpect(jsonPath("$.errors.similarityThreshold").exists());
        verifyNoInteractions(searchService);
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON).content("{\"query\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.query").value("query must not be blank"));
    }

    @Test
    void invalidFilterIs400() throws Exception {
        when(searchService.search(anyString(), anyInt(), anyDouble(), any()))
                .thenThrow(new InvalidRequestException("Invalid filterExpression"));

        mockMvc.perform(post("/api/search").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\": \"q\", \"filterExpression\": \"department ==== HR\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid filterExpression"));
    }
}
