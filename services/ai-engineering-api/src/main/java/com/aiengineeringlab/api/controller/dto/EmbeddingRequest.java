package com.aiengineeringlab.api.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EmbeddingRequest(
        @NotBlank(message = "text must not be blank")
        @Size(max = ApiLimits.MAX_TEXT_LENGTH, message = "text must be at most {max} characters")
        String text) {
}
