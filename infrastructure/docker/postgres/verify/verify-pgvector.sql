-- Proves that pgvector is installed AND usable: type, operators, and an HNSW index.
-- Everything runs inside a transaction that is rolled back, so nothing is left behind.
\set ON_ERROR_STOP on
\pset footer off

BEGIN;

SELECT extname AS extension, extversion AS version
FROM pg_extension
WHERE extname = 'vector';

CREATE TEMP TABLE pgvector_smoke_test (
    id        text PRIMARY KEY,
    embedding vector(3)
);

INSERT INTO pgvector_smoke_test (id, embedding) VALUES
    ('same-direction',     '[1, 1, 0]'),
    ('similar-direction',  '[1, 0.8, 0.1]'),
    ('opposite-direction', '[-1, -1, 0]'),
    ('orthogonal',         '[0, 0, 1]');

CREATE INDEX ON pgvector_smoke_test USING hnsw (embedding vector_cosine_ops);

-- <=> is cosine distance (0 = identical direction, 1 = unrelated, 2 = opposite).
SELECT id,
       round((embedding <=> '[1, 1, 0]')::numeric, 4)     AS cosine_distance,
       round((1 - (embedding <=> '[1, 1, 0]'))::numeric, 4) AS cosine_similarity
FROM pgvector_smoke_test
ORDER BY embedding <=> '[1, 1, 0]'
LIMIT 4;

ROLLBACK;

SELECT 'pgvector OK' AS result;
