package com.aiengineeringlab.api.exception;

/**
 * A request that passed bean validation but is still semantically invalid, for example a metadata
 * filter expression that cannot be parsed. Mapped to HTTP 400.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
