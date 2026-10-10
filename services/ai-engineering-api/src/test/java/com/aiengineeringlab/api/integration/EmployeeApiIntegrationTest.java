package com.aiengineeringlab.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.support.FakeEmbeddingModelConfiguration;
import com.aiengineeringlab.api.support.PgVectorTestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * HTTP → controller → service → JPA → real PostgreSQL (Testcontainers), with the schema created by Flyway V5.
 * Skipped automatically when Docker is not available.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({PgVectorTestcontainersConfiguration.class, FakeEmbeddingModelConfiguration.class})
@Testcontainers(disabledWithoutDocker = true)
class EmployeeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void fullCrudLifecycleAgainstPostgres() throws Exception {
        String created = mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Asha\", \"department\": \"Engineering\", \"email\": \"asha@example.com\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = JsonPath.read(created, "$.id");
        assertThat(id.longValue()).isPositive();

        mockMvc.perform(get("/api/employees/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + id + ")].email").value("asha@example.com"));

        mockMvc.perform(put("/api/employees/" + id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Asha R\", \"department\": \"Platform\", \"email\": \"asha.r@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.longValue()))
                .andExpect(jsonPath("$.department").value("Platform"));

        mockMvc.perform(get("/api/employees/" + id))
                .andExpect(jsonPath("$.name").value("Asha R"));

        mockMvc.perform(delete("/api/employees/" + id)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/employees/" + id)).andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/employees/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void missingEmployeeIs404AndInvalidBodyIs400() throws Exception {
        mockMvc.perform(get("/api/employees/987654321")).andExpect(status().isNotFound());
        mockMvc.perform(put("/api/employees/987654321").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"A\", \"department\": \"B\", \"email\": \"a@example.com\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"A\", \"department\": \"B\", \"email\": \"bad\"}"))
                .andExpect(status().isBadRequest());
    }
}
