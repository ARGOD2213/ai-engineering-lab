package com.aiengineeringlab.api.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * @param id optional client-chosen id. Re-sending the same id updates the document (upsert), which
 *     makes loading the sample dataset idempotent. Omit it to get a random UUID.
 * @param text the text to embed and store
 * @param metadata optional filterable attributes, for example {"department": "HR"}
 */
public record CreateDocumentRequest(
        @Size(max = 128, message = "id must be at most {max} characters")
        @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "id may only contain letters, digits, '.', '_', ':' and '-'")
        @Nullable String id,

        @NotBlank(message = "text must not be blank")
        @Size(max = ApiLimits.MAX_TEXT_LENGTH, message = "text must be at most {max} characters")
        String text,

        @Size(max = ApiLimits.MAX_METADATA_ENTRIES, message = "metadata may contain at most {max} entries")
        @Nullable Map<String, Object> metadata) {
}
