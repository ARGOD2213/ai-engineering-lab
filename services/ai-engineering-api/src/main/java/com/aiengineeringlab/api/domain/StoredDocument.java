package com.aiengineeringlab.api.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A document as it is persisted in the vector table.
 *
 * @param id document id
 * @param text the original text (kept next to the vector because a vector cannot be turned back into text)
 * @param metadata filterable attributes
 * @param embeddingDimensions size of the stored vector, read back from PostgreSQL with vector_dims()
 * @param embeddingModel model whose vector space this document lives in
 * @param createdAt set by the database on first insert
 * @param embedding the stored vector, only included when explicitly requested
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoredDocument(
        String id,
        String text,
        Map<String, Object> metadata,
        int embeddingDimensions,
        String embeddingModel,
        OffsetDateTime createdAt,
        float @Nullable [] embedding) {
}
