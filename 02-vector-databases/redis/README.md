# Redis Vector Search (milestone 2, planned)

**Not implemented yet. pgvector is milestone 1.**

What we will look at:
- Redis with the query engine (vector search) in Docker (Compose profile, not started by default)
- `spring-ai-starter-vector-store-redis`: index schema, which metadata fields must be declared up front for filtering
- in-memory trade-offs: speed vs. RAM cost vs. durability
- re-running `SemanticRetrievalEvaluationTest` and comparing with pgvector
