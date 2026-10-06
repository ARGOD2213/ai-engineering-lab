# Qdrant (milestone 2, planned)

**Not implemented yet. pgvector is milestone 1.**

What we will look at:
- running Qdrant in Docker (Compose profile, not started by default)
- `spring-ai-starter-vector-store-qdrant` (gRPC client, collections, payload)
- payload indexes vs. pgvector's JSONB GIN index for metadata filtering
- HNSW configuration in a purpose-built vector database vs. in PostgreSQL
- re-running `SemanticRetrievalEvaluationTest` and comparing results and latency
