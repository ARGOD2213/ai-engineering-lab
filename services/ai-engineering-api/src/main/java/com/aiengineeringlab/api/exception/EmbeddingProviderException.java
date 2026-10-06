package com.aiengineeringlab.api.exception;

/**
 * The embedding model could not produce a vector (provider down, rate limited, bad credentials,
 * local model failed to load). Mapped to HTTP 503 without exposing provider details to clients.
 */
public class EmbeddingProviderException extends RuntimeException {

    public EmbeddingProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
