-- Vector table for the opt-in OpenAI model: text-embedding-3-small (1536 dimensions).
--
-- A separate table, not a separate column type in the same table: vectors produced by different
-- models live in different "meaning spaces". Comparing a MiniLM vector with an OpenAI vector is
-- meaningless even if the sizes happened to match. Switching models therefore means re-embedding
-- every document into the new table.
CREATE TABLE documents_openai_text_embedding_3_small (
    id          TEXT         PRIMARY KEY,
    content     TEXT         NOT NULL,
    metadata    JSONB        NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(1536) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- HNSW supports up to 2000 dimensions for the vector type, so 1536 is fine.
CREATE INDEX documents_openai_te3s_embedding_hnsw_idx
    ON documents_openai_text_embedding_3_small USING hnsw (embedding vector_cosine_ops);

CREATE INDEX documents_openai_te3s_metadata_idx
    ON documents_openai_text_embedding_3_small USING gin (metadata jsonb_path_ops);
