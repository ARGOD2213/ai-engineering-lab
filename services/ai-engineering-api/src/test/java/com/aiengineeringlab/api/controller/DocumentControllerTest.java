package com.aiengineeringlab.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.domain.BatchIngestionResult;
import com.aiengineeringlab.api.domain.NewDocument;
import com.aiengineeringlab.api.domain.StoredDocument;
import com.aiengineeringlab.api.exception.DocumentNotFoundException;
import com.aiengineeringlab.api.exception.VectorStoreUnavailableException;
import com.aiengineeringlab.api.service.DocumentIngestionService;
import com.aiengineeringlab.api.support.TestMetricsConfiguration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
@Import(TestMetricsConfiguration.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentIngestionService ingestionService;

    @Test
    void createsADocumentAndReturnsLocation() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(new StoredDocument("doc-1",
                "Employees can take up to 5 days of emergency leave.", Map.of("department", "HR"), 384, "mock-model",
                OffsetDateTime.parse("2026-01-01T10:00:00Z"), null));

        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("""
                        {
                          "text": "Employees can take up to 5 days of emergency leave.",
                          "metadata": {"department": "HR", "documentType": "POLICY"}
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/documents/doc-1"))
                .andExpect(jsonPath("$.id").value("doc-1"))
                .andExpect(jsonPath("$.embeddingDimensions").value(384))
                .andExpect(jsonPath("$.metadata.department").value("HR"))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T10:00:00Z"))
                // the vector is only returned on explicit request
                .andExpect(jsonPath("$.embedding").doesNotExist());

        ArgumentCaptor<NewDocument> captor = ArgumentCaptor.forClass(NewDocument.class);
        verify(ingestionService).ingest(captor.capture());
        assertThat(captor.getValue().metadata()).containsEntry("documentType", "POLICY");
        assertThat(captor.getValue().id()).isNull();
    }

    @Test
    void metadataIsOptional() throws Exception {
        when(ingestionService.ingest(any())).thenReturn(new StoredDocument("doc-1", "t", Map.of(), 384, "m",
                OffsetDateTime.now(), null));

        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"t\"}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<NewDocument> captor = ArgumentCaptor.forClass(NewDocument.class);
        verify(ingestionService).ingest(captor.capture());
        assertThat(captor.getValue().metadata()).isEmpty();
    }

    @Test
    void rejectsMissingTextAndUnsafeIds() throws Exception {
        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\": \"../../etc\", \"metadata\": {}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.text").value("text must not be blank"))
                .andExpect(jsonPath("$.errors.id").exists());
        verifyNoInteractions(ingestionService);
    }

    @Test
    void validatesEveryDocumentOfABatch() throws Exception {
        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": [{\"text\": \"ok\"}, {\"text\": \"\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['documents[1].text']").value("text must not be blank"));
        verifyNoInteractions(ingestionService);
    }

    @Test
    void rejectsAnEmptyBatch() throws Exception {
        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON).content("{\"documents\": []}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void storesABatch() throws Exception {
        when(ingestionService.ingestAll(anyList()))
                .thenReturn(new BatchIngestionResult(2, List.of("a", "b"), "mock-model", 384));

        mockMvc.perform(post("/api/documents/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documents\": [{\"id\": \"a\", \"text\": \"one\"}, {\"id\": \"b\", \"text\": \"two\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stored").value(2))
                .andExpect(jsonPath("$.ids[1]").value("b"));
    }

    @Test
    void unknownDocumentIs404() throws Exception {
        when(ingestionService.findById("nope", false)).thenThrow(new DocumentNotFoundException("nope"));

        mockMvc.perform(get("/api/documents/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Document 'nope' was not found"));
    }

    @Test
    void databaseOutageIs503() throws Exception {
        when(ingestionService.ingest(any()))
                .thenThrow(new VectorStoreUnavailableException("down", new RuntimeException("connection refused")));

        mockMvc.perform(post("/api/documents").contentType(MediaType.APPLICATION_JSON).content("{\"text\": \"t\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.title").value("Vector store unavailable"));
    }
}
