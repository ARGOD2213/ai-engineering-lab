# Developer notes: the "why" behind milestone 1

Short answers, each tied to code in `services/ai-engineering-api`. Ask ChatGPT to go deeper on any of them.

### Why do embeddings exist?
Computers compare numbers easily but meaning poorly. An embedding model turns text into a vector such that
*similar meaning means nearby vectors*. Search then becomes geometry: find the nearest vectors.
→ `EmbeddingService.embed`

### Why does the embedding model create the vector instead of the vector database?
Creating vectors needs a trained neural network. Storing and comparing them needs an index. They are different jobs.
pgvector only stores `float[]` and computes distances; it has no idea what words mean. This separation lets
you change either side independently. In Spring AI, `VectorStore.add()` *calls* the `EmbeddingModel` for you,
so it can look like the database embeds, but it does not.
→ `DocumentIngestionService` (note: it never calls the model directly)

### Why does vector dimension matter?
It is the length of every vector. It drives storage (384 × 4 B ≈ 1.5 KB per row), index size, and distance
computation cost. A bigger dimension is not automatically better quality.

### Why must the vector dimension match the embedding model?
The column is declared `vector(384)`. PostgreSQL rejects a vector of any other length, and distance between
vectors of different lengths is undefined. The app checks this at startup (`EmbeddingDimensionVerifier`), and so
does Spring AI (`schema-validation: true`).
→ test `pgvectorRejectsAVectorOfTheWrongDimension`

### Why do we retain the original text?
A vector cannot be turned back into text. Search results must show the words, and later the RAG step must send
the actual text to the LLM. The `content` column stores it next to the vector.

### Why do we store metadata?
To filter (`department == 'HR'`), to show provenance (`source`), and later to enforce access control.
Similarity alone cannot express "only HR documents".
→ `filterExpression` in `POST /api/search`, GIN index in `V2__...sql`

### Why do we need Top-K?
Similarity search *always* returns the nearest vectors, even when nothing is truly relevant. Top-K bounds the
result size (and later, the LLM prompt size and cost). A `similarityThreshold` additionally drops weak matches.

### Why can keyword search and vector search produce different results?
Keyword search matches spelling: "leave a review" matches "casual leave". Vector search matches meaning:
"time off when a relative passes away" finds *emergency leave* with zero shared keywords. Each fails differently.
Vectors are weak at exact identifiers, codes and numbers, which is why production systems often combine both
(hybrid search, a later topic).
→ the evaluation test prints both rankings side by side

### Why might changing embedding models require re-indexing?
Each model has its own coordinate system. Old vectors and new query vectors would be compared across two
different spaces, which produces meaningless results. Every document must be re-embedded with the new model.
→ one table per model: `V2__...` (MiniLM) and `V3__...` (OpenAI)

### Why is the LLM intentionally absent at this stage?
Retrieval quality is the foundation of RAG. If the right documents are not retrieved, no LLM can answer
correctly, and it will hallucinate convincingly. Measuring retrieval alone, without an LLM, keeps the
problem observable and cheap. `spring.ai.model.chat: none` guarantees no chat model is even created.

### Why is pgvector used first?
It is PostgreSQL, which you already know: SQL, transactions, backups, joins with business data, one fewer system
to operate. It also makes the internals visible (`vector(384)`, `<=>`, `CREATE INDEX ... USING hnsw`).
See [ADR-001](../../docs/decisions/ADR-001-pgvector.md).

### Why cosine distance?
Cosine compares direction and ignores length. Text embeddings encode meaning in direction, and this model's
vectors are not normalised (length ≈ 6.7), so inner product would be biased by length. pgvector operator: `<=>`.

### Why does a similarity threshold of 0 still hide some documents?
Spring AI treats 0.0 as "accept all", but PgVectorStore implements it as `distance < 1`, so documents with a
**negative** cosine similarity are excluded. With a strict metadata filter you can therefore get fewer than K results.
