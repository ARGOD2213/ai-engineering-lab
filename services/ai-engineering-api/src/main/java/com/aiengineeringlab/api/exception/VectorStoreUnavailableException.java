package com.aiengineeringlab.api.exception;

/** The vector database could not be reached or rejected the operation. Mapped to HTTP 503. */
public class VectorStoreUnavailableException extends RuntimeException {

    public VectorStoreUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
