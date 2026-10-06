package com.aiengineeringlab.api.controller.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Several documents embedded with one batched model call - cheaper and faster than one call each. */
public record BatchCreateDocumentsRequest(
        @NotEmpty(message = "documents must not be empty")
        @Size(max = ApiLimits.MAX_BATCH_SIZE, message = "at most {max} documents per batch")
        List<@Valid CreateDocumentRequest> documents) {
}
