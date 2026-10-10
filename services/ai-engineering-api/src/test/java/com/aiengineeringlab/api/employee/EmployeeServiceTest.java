package com.aiengineeringlab.api.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository repository;

    @org.mockito.InjectMocks
    private EmployeeService service;

    @Test
    void createIgnoresAClientSuppliedIdAndSaves() {
        Employee input = new Employee("Asha", "Engineering", "asha@example.com");
        input.setId(99L);
        when(repository.save(any(Employee.class))).thenAnswer(call -> call.getArgument(0));

        Employee saved = service.create(input);

        assertThat(saved.getId()).isNull();
        verify(repository).save(input);
    }

    @Test
    void findAllReturnsEveryEmployee() {
        List<Employee> all = List.of(new Employee("A", "X", "a@example.com"), new Employee("B", "Y", "b@example.com"));
        when(repository.findAll()).thenReturn(all);

        assertThat(service.findAll()).isEqualTo(all);
    }

    @Test
    void findByIdReturnsTheEmployee() {
        Employee employee = new Employee("Asha", "Engineering", "asha@example.com");
        when(repository.findById(1L)).thenReturn(Optional.of(employee));

        assertThat(service.findById(1L)).isSameAs(employee);
    }

    @Test
    void findByIdThrowsWhenMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(404L))
                .isInstanceOf(EmployeeNotFoundException.class)
                .hasMessage("Employee 404 was not found");
    }

    @Test
    void updateCopiesTheFieldsOntoTheExistingEmployee() {
        Employee existing = new Employee("Asha", "Engineering", "asha@example.com");
        existing.setId(1L);
        when(repository.findById(1L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Employee result = service.update(1L, new Employee("Asha R", "Platform", "asha.r@example.com"));

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Asha R");
        assertThat(result.getDepartment()).isEqualTo("Platform");
        assertThat(result.getEmail()).isEqualTo("asha.r@example.com");
    }

    @Test
    void updateThrowsWhenMissing() {
        when(repository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(404L, new Employee("A", "B", "a@example.com")))
                .isInstanceOf(EmployeeNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void deleteRemovesAnExistingEmployee() {
        when(repository.existsById(1L)).thenReturn(true);

        service.delete(1L);

        verify(repository).deleteById(1L);
    }

    @Test
    void deleteThrowsWhenMissing() {
        when(repository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(404L)).isInstanceOf(EmployeeNotFoundException.class);
        verify(repository, never()).deleteById(any());
    }
}
