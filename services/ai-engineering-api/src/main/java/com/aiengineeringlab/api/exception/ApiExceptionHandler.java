package com.aiengineeringlab.api.exception;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates exceptions into RFC 9457 problem details.
 * <p>
 * Developer note: clients get a stable, generic message. Provider error messages can contain
 * request details or partially masked credentials, so they are never copied into responses and
 * only the exception type is logged at WARN level.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final MeterRegistry meterRegistry;

    public ApiExceptionHandler(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    protected @Nullable ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        countError("validation");
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setTitle("Invalid request");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(InvalidRequestException.class)
    ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        countError("invalid_request");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid request");
        return problem;
    }

    @ExceptionHandler(DocumentNotFoundException.class)
    ProblemDetail handleNotFound(DocumentNotFoundException ex) {
        countError("not_found");
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Document not found");
        return problem;
    }

    @ExceptionHandler(EmbeddingProviderException.class)
    ProblemDetail handleEmbeddingProvider(EmbeddingProviderException ex) {
        countError("embedding_provider");
        log.warn("Embedding provider failure: {} (cause: {})", ex.getMessage(), causeType(ex));
        log.debug("Embedding provider failure details", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The embedding model is currently unavailable. Please retry later.");
        problem.setTitle("Embedding provider unavailable");
        return problem;
    }

    @ExceptionHandler(VectorStoreUnavailableException.class)
    ProblemDetail handleVectorStore(VectorStoreUnavailableException ex) {
        countError("vector_store");
        log.warn("Vector store failure: {} (cause: {})", ex.getMessage(), causeType(ex));
        log.debug("Vector store failure details", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The vector store is currently unavailable. Please retry later.");
        problem.setTitle("Vector store unavailable");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        countError("unexpected");
        log.error("Unexpected error", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
        problem.setTitle("Internal server error");
        return problem;
    }

    private void countError(String type) {
        meterRegistry.counter("lab.api.errors", "type", type).increment();
    }

    private static String causeType(Throwable ex) {
        return ex.getCause() != null ? ex.getCause().getClass().getName() : "none";
    }
}
