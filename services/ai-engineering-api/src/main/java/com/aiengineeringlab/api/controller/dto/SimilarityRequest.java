package com.aiengineeringlab.api.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SimilarityRequest(
        @NotNull(message = "texts is required")
        @Size(min = 2, max = 10, message = "texts must contain between {min} and {max} entries")
        List<@NotBlank(message = "texts must not contain blank entries")
             @Size(max = ApiLimits.MAX_TEXT_LENGTH, message = "each text must be at most {max} characters") String> texts) {
}
