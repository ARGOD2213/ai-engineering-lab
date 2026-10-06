# 02 - Vector Databases

> Store vectors, then find the nearest ones to a query vector, fast.

## What

A vector database (or a database with a vector extension) stores vectors next to their data and answers
**"which K stored vectors are closest to this one?"**, the nearest-neighbour query. It does not create vectors;
the embedding model does that (see [01-embeddings](../01-embeddings/README.md)).

Core concepts:

| Concept | In pgvector (milestone 1) |
|---|---|
| Vector column with fixed dimension | `embedding vector(384)` |
| Distance metric | cosine distance `<=>` (also `<->` L2, `<#>` negative inner product) |
| Exact search | sequential scan + sort (always correct, O(n)) |
| Approximate (ANN) index | `USING hnsw (embedding vector_cosine_ops)` (fast, may miss a neighbour) |
| Metadata filter | `metadata @@ '$.department == "HR"'` (jsonpath on a JSONB column) |

## Why

A brute-force scan over millions of 384-dimensional vectors is too slow per query. ANN indexes such as HNSW
trade a little recall for orders-of-magnitude speed. Keeping vectors next to their text and metadata lets one
query return everything a result needs.

## How (in this lab)

```text
02-vector-databases/
├── dataset/      # 30 documents + 16 evaluation queries, independent of any database
├── scripts/      # load-dataset.sh, search.sh (curl only)
├── pgvector/     # milestone 1: implemented and measured
├── chroma/       # milestone 2: planned
├── qdrant/       # milestone 2: planned
└── redis/        # milestone 2: planned
```

Run the experiment against the running service:

```bash
./02-vector-databases/scripts/load-dataset.sh
./02-vector-databases/scripts/search.sh "How much time off do I get when a close relative suddenly passes away?" 3
./02-vector-databases/scripts/search.sh "leave policy" 3 "department == 'HR'"
```

Or run the full automated evaluation (real model + throw-away pgvector container):

```bash
cd services/ai-engineering-api && ./mvnw test -Preal-model
# report: services/ai-engineering-api/target/semantic-retrieval-report.md
```

## "What changes when the vector database changes?"

This is the question milestone 2 answers. The application is built so that only one layer changes:

| Layer | Changes when the store changes? |
|---|---|
| Controllers, DTOs, validation | no |
| `EmbeddingService`, embedding model | no (same model, same vectors) |
| `DocumentIngestionService`, `SemanticSearchService` | no (they use Spring AI's `VectorStore`, `Document`, `SearchRequest`) |
| Filter expressions (`department == 'HR'`) | no: Spring AI's portable syntax is translated per store |
| Maven starter + `spring.ai.vectorstore.*` properties | **yes** |
| Docker service | **yes** |
| Schema/collection creation, index type and parameters | **yes** |
| Score semantics (distance vs. similarity, thresholds) | **yes**, subtly (see the pgvector threshold gotcha) |
| `DocumentRepository` (raw SQL read side) | **yes**, because it is pgvector-specific by design |
| Operational concerns: backups, scaling, memory, filtering performance | **yes** |

The plan for milestone 2: keep the same dataset, queries, model and services; add one store at a time
(Compose profile + Spring profile); re-run `SemanticRetrievalEvaluationTest`; compare results, latency and
operational effort. Rankings should be nearly identical because they come from the model. Differences come from
ANN approximations, filtering behaviour and scoring conventions.

## Production considerations

- **Recall vs. latency:** HNSW parameters (`m`, `ef_construction`, query-time `hnsw.ef_search`) trade accuracy for speed. Measure, do not guess.
- **Filtering + ANN:** filtering after an approximate scan can return fewer than K rows. pgvector 0.8 adds
  iterative index scans (`hnsw.iterative_scan`) for this.
- **Memory:** HNSW indexes are fastest when they fit in RAM. 1M × 1536-d float vectors ≈ 6 GB before index overhead.
- **One embedding model per index.** A model change means a full re-index.
- **Updates and deletes** are real costs for graph indexes. Plan re-index windows.

## Interview questions

See [docs/interview/vector-databases.md](../docs/interview/vector-databases.md).
