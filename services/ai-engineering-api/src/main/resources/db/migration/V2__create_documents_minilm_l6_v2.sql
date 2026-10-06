-- Vector table for the default local model: sentence-transformers/all-MiniLM-L6-v2 (384 dimensions).
--
-- Columns id, content, metadata and embedding are the contract expected by Spring AI's PgVectorStore.
-- created_at is our own addition; PgVectorStore never writes it, so the database default fills it.
CREATE TABLE documents_minilm_l6_v2 (
    id          TEXT        PRIMARY KEY,
    -- The original text. The vector alone cannot be turned back into text, and search results
    -- must show (and later, RAG must send to the LLM) the actual words.
    content     TEXT        NOT NULL,
    -- Filterable attributes (department, documentType, source, ...).
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    -- Must equal the model's output size. Inserting a 1536-d vector here fails.
    embedding   vector(384) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- HNSW = approximate nearest-neighbour graph index. vector_cosine_ops must match the operator used
-- by queries (<=>, cosine distance); an index built for another operator is simply not used.
CREATE INDEX documents_minilm_l6_v2_embedding_hnsw_idx
    ON documents_minilm_l6_v2 USING hnsw (embedding vector_cosine_ops);

-- Supports metadata filters, which PgVectorStore translates to jsonpath: metadata @@ '$.department == "HR"'.
CREATE INDEX documents_minilm_l6_v2_metadata_idx
    ON documents_minilm_l6_v2 USING gin (metadata jsonb_path_ops);
