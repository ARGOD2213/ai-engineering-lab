package com.aiengineeringlab.api.employee;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aiengineeringlab.api.support.TestMetricsConfiguration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EmployeeController.class)
@Import(TestMetricsConfiguration.class)
class EmployeeControllerTest {

    private static final String VALID_BODY = """
            {"name": "Asha", "department": "Engineering", "email": "asha@example.com"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EmployeeService service;

    private static Employee employee(long id, String name) {
        Employee employee = new Employee(name, "Engineering", name.toLowerCase() + "@example.com");
        employee.setId(id);
        return employee;
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(service.create(any())).thenReturn(employee(1, "Asha"));

        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/employees/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Asha"))
                .andExpect(jsonPath("$.department").value("Engineering"))
                .andExpect(jsonPath("$.email").value("asha@example.com"));
    }

    @Test
    void listReturns200() throws Exception {
        when(service.findAll()).thenReturn(List.of(employee(1, "Asha"), employee(2, "Ravi")));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[1].name").value("Ravi"));
    }

    @Test
    void getByIdReturns200() throws Exception {
        when(service.findById(1L)).thenReturn(employee(1, "Asha"));

        mockMvc.perform(get("/api/employees/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void getMissingEmployeeReturns404() throws Exception {
        when(service.findById(404L)).thenThrow(new EmployeeNotFoundException(404L));

        mockMvc.perform(get("/api/employees/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee 404 was not found"));
    }

    @Test
    void updateReturns200() throws Exception {
        when(service.update(org.mockito.ArgumentMatchers.eq(1L), any())).thenReturn(employee(1, "Asha"));

        mockMvc.perform(put("/api/employees/1").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"));
    }

    @Test
    void updateMissingEmployeeReturns404() throws Exception {
        when(service.update(org.mockito.ArgumentMatchers.eq(404L), any())).thenThrow(new EmployeeNotFoundException(404L));

        mockMvc.perform(put("/api/employees/404").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/employees/1")).andExpect(status().isNoContent());

        verify(service).delete(1L);
    }

    @Test
    void deleteMissingEmployeeReturns404() throws Exception {
        org.mockito.Mockito.doThrow(new EmployeeNotFoundException(404L)).when(service).delete(404L);

        mockMvc.perform(delete("/api/employees/404")).andExpect(status().isNotFound());
    }

    @Test
    void invalidBodyOnCreateReturns400() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"email\": \"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("name must not be blank"))
                .andExpect(jsonPath("$.errors.department").value("department must not be blank"))
                .andExpect(jsonPath("$.errors.email").value("email must be a valid email address"));
        verifyNoInteractions(service);
    }

    @Test
    void invalidBodyOnUpdateReturns400() throws Exception {
        mockMvc.perform(put("/api/employees/1").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/api/employees").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest());
    }
}
