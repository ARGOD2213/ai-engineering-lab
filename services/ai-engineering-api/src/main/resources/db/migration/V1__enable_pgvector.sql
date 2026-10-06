-- pgvector adds:
--   * the vector(n) column type
--   * distance operators: <-> (Euclidean/L2), <=> (cosine), <#> (negative inner product)
--   * approximate nearest-neighbour indexes: HNSW and IVFFlat
-- In managed databases the extension must be allow-listed; creating it may need elevated rights.
CREATE EXTENSION IF NOT EXISTS vector;
