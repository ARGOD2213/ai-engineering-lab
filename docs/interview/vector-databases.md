# Interview prep: vector databases

## Fundamentals

**1. What does a vector database do that a normal index does not?**
It answers nearest-neighbour queries ("the K vectors closest to this one") under a distance metric. B-trees answer
equality and range queries on scalar values, which is useless for 384-dimensional similarity.

**2. Does the vector database understand text?**
No. It stores float arrays and computes distances. The embedding model creates the vectors. In Spring AI,
`VectorStore.add()` calls the `EmbeddingModel` and then writes, which is easy to confuse with "the DB embeds".

**3. Exact vs. approximate nearest-neighbour search?**
Exact = compare the query with every vector (always correct, O(n)). Approximate (HNSW, IVFFlat) = navigate an index to
find *probably* the nearest neighbours much faster. You trade recall for latency, tuned with parameters such as `ef_search`.

**4. Explain HNSW in two sentences.**
A layered graph where each vector links to its near neighbours. Search starts in a sparse top layer and greedily descends
to denser layers, so it visits a tiny fraction of the vectors.

**5. Which distance metric and why must the index match?**
Cosine for text embeddings (direction matters, length does not). In pgvector the index is built per operator class
(`vector_cosine_ops`). A query using `<->` (L2) would not use a cosine index.

**6. Why store the original text and metadata next to the vector?**
A vector cannot be decoded back to text. Results need the text (and RAG must send it to the LLM). Metadata enables
filtering (`department == 'HR'`), provenance and access control.

## Engineering

**7. Why pgvector first instead of a dedicated vector DB?**
Familiar PostgreSQL, one system to operate, transactions, joins with business data, fully inspectable. Dedicated stores
may win at very large scale or very high QPS. (ADR-001)

**8. A filtered search with topK=5 returns only 1 result. Why?**
Two possible reasons we saw or know: (a) the similarity threshold excluded the rest. Spring AI's PgVectorStore turns
threshold 0 into `distance < 1`, which drops negative-similarity rows. (b) With an approximate index, rows are filtered
*after* the index scan returns `ef_search` candidates, so a selective filter can leave fewer than K. pgvector 0.8 iterative scans address (b).

**9. Similarity search always returns something. How do you avoid irrelevant results?**
A similarity threshold calibrated per model on real queries (scores are not probabilities: 0.35 was a correct match
and 0.57 a wrong one in our data), metadata filters, and later a re-ranker or an LLM step that may answer "not found".

**10. How do you evaluate retrieval quality?**
A labelled set of queries with expected documents; measure hit@1 / hit@K (and MRR/nDCG at scale). Keep it in the repo,
run it in CI on every model or index change. Ours: 16 queries; vector hit@1 14/16, hit@3 16/16 vs keyword hit@1 11/16.

**11. What changes if you move from pgvector to Qdrant?**
Starter dependency, properties, the Docker service, collection/index configuration, score conventions and operations.
*Not* the service code: it uses Spring AI's `VectorStore`, `SearchRequest` and portable filter expressions.

**12. Capacity estimate: 10M documents, 1536-d float vectors?**
10M × 1536 × 4 B ≈ 61 GB of raw vectors, plus index overhead (HNSW graph links) and the text and metadata. Consider
smaller or shortened-dimension embeddings, `halfvec` (2 bytes per dimension), partitioning, or a dedicated store.

**13. Keyword search vs. vector search: when does each win?**
Vector: paraphrases and natural-language questions. Keyword: exact terms, codes, names, numbers. Production systems
often combine both (hybrid search with rank fusion).

## Stretch (ask ChatGPT)

- IVFFlat vs. HNSW: build time, memory, recall, updates.
- What is product quantisation and when would you use it?
- How does Reciprocal Rank Fusion combine keyword and vector rankings?
