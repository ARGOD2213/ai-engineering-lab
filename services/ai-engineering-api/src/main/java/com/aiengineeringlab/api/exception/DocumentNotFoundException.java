package com.aiengineeringlab.api.exception;

/** No document with the requested id exists in the vector table. Mapped to HTTP 404. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(String id) {
        super("Document '%s' was not found".formatted(id));
    }
}
