package com.aiengineeringlab.api.controller.dto;

/**
 * Input size limits.
 * <p>
 * Developer note: the limit protects the service (memory, provider cost), it does NOT guarantee the
 * model reads the whole text. all-MiniLM-L6-v2 silently truncates after 128 tokens (~90 English
 * words) and OpenAI's text-embedding-3-small after 8191 tokens. Long documents must be chunked
 * before embedding - that is a milestone 3 (RAG) topic.
 */
public final class ApiLimits {

    public static final int MAX_TEXT_LENGTH = 8_000;
    public static final int MAX_QUERY_LENGTH = 2_000;
    public static final int MAX_TOP_K = 20;
    public static final int MAX_BATCH_SIZE = 100;
    public static final int MAX_METADATA_ENTRIES = 20;

    private ApiLimits() {
    }
}
