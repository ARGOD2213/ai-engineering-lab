package com.aiengineeringlab.api.employee;

/** No employee with the requested id exists. Mapped to HTTP 404 by ApiExceptionHandler. */
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(Long id) {
        super("Employee %d was not found".formatted(id));
    }
}
