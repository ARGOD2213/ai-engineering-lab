# pgvector (milestone 1)

PostgreSQL 17 + pgvector 0.8.7, run with Docker Compose and accessed through Spring AI's `PgVectorStore`.
See [ADR-001](../../docs/decisions/ADR-001-pgvector.md) for why it is first.

## Setup

```bash
cp .env.example .env                               # once; set POSTGRES_PASSWORD
docker compose up -d                               # from the repository root
docker compose ps                                  # wait for "healthy"
./infrastructure/docker/postgres/verify-pgvector.sh
```

`verify-pgvector.sh` proves the extension is **installed and usable**. It creates a temporary 3-d vector table,
builds an HNSW index, and runs a cosine query, all inside a rolled-back transaction:

```text
 extension | version
-----------+---------
 vector    | 0.8.7

         id         | cosine_distance | cosine_similarity
--------------------+-----------------+-------------------
 same-direction     |          0.0000 |            1.0000
 similar-direction  |          0.0091 |            0.9909
 orthogonal         |          1.0000 |            0.0000
 opposite-direction |          2.0000 |           -1.0000
```

## Schema (owned by Flyway)

```sql
-- V2__create_documents_minilm_l6_v2.sql
CREATE TABLE documents_minilm_l6_v2 (
    id          TEXT        PRIMARY KEY,
    content     TEXT        NOT NULL,
    metadata    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    embedding   vector(384) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ... USING hnsw (embedding vector_cosine_ops);   -- nearest-neighbour index
CREATE INDEX ... USING gin  (metadata jsonb_path_ops);       -- metadata filters
```

Spring AI's `initialize-schema` is **off**. Versioned migrations create the schema; `PgVectorStore` only validates it
(`schema-validation: true`: table exists, columns exist, `vector(N)` matches). The OpenAI table (`vector(1536)`) is
created by `V3__...sql`.

## What Spring AI sends to PostgreSQL

**Insert** (`VectorStore.add`). The model is called first, then one upsert per document:

```sql
INSERT INTO public.documents_minilm_l6_v2 (id, content, metadata, embedding)
VALUES (?, ?, ?::jsonb, ?)
ON CONFLICT (id) DO UPDATE SET content = ?, metadata = ?::jsonb, embedding = ?
```

**Search** (`VectorStore.similaritySearch`). The query is embedded first, then:

```sql
SELECT *, embedding <=> ? AS distance
FROM public.documents_minilm_l6_v2
WHERE embedding <=> ? < ?            -- ? = 1 - similarityThreshold
  [AND metadata::jsonb @@ '$.department == "HR"'::jsonpath]
ORDER BY distance
LIMIT ?                              -- topK
```

`score = 1 - distance` = cosine similarity.

Is the HNSW index used? With only 30 rows the planner rightly prefers a sequential scan. Forcing the planner shows the index works:

```text
SET enable_seqscan = off; EXPLAIN SELECT ... ORDER BY embedding <=> (...) LIMIT 5;
 Limit
   ->  Index Scan using documents_minilm_l6_v2_embedding_hnsw_idx on documents_minilm_l6_v2
         Order By: (embedding <=> (InitPlan 1).col1)
```

## Experiment results

Real model (all-MiniLM-L6-v2, 384-d), real pgvector, the 30-document [dataset](../dataset/README.md), 16 queries, top-3.
Produced by `SemanticRetrievalEvaluationTest` (`./mvnw test -Preal-model`).

| Query | Challenge | Expected | Vector #1 (score) | Rank of expected | Naive keyword #1 |
|---|---|---|---|---|---|
| How many emergency leave days can an employee take? | DIRECT | hr-emergency-leave | hr-emergency-leave (0.829) | 1 | hr-emergency-leave (ok) |
| How much time off do I get when a close relative suddenly passes away? | PARAPHRASE | hr-emergency-leave | hr-emergency-leave (0.436) | 1 | spring-dependency-injection (wrong) |
| Can I send back something I bought last week? | PARAPHRASE | faq-returns | faq-returns (0.546) | 1 | hr-remote-work (wrong) |
| How do I leave feedback on something I purchased? | KEYWORD_TRAP | faq-leave-review | faq-leave-review (0.571) | 1 | faq-leave-review (ok) |
| How are beans managed in a Spring application? | KEYWORD_TRAP | spring-beans | spring-beans (0.762) | 1 | spring-beans (ok) |
| Which coffee should I buy for my morning espresso? | PARAPHRASE | product-coffee-beans | product-coffee-beans (0.354) | 1 | product-coffee-beans (ok) |
| How do lightweight threads help a server handle many concurrent requests? | KEYWORD_TRAP | java-virtual-threads | java-virtual-threads (0.494) | 1 | java-virtual-threads (ok) |
| What is the thread count of the bedding? | KEYWORD_TRAP | product-bed-sheets | product-bed-sheets (0.611) | 1 | product-bed-sheets (ok) |
| What happens if a borrower stops paying their instalments? | PARAPHRASE | loan-default | **loan-prepayment (0.570)** | **2** | loan-default (ok) |
| Which port does a Spring Boot app listen on if I don't configure one? | DIRECT | spring-default-port | spring-default-port (0.775) | 1 | spring-default-port (ok) |
| Can I close my loan early without paying a fee? | PARAPHRASE | loan-prepayment | loan-prepayment (0.470) | 1 | loan-default (wrong) |
| How can I check that my running service is healthy? | AMBIGUOUS | spring-actuator / tech-docker-healthcheck | tech-docker-healthcheck (0.469) | 1 | tech-docker-healthcheck (ok) |
| The API keeps rejecting my calls because I'm sending too many | PARAPHRASE | tech-http-429 | tech-http-429 (0.521) | 1 | tech-http-429 (ok) |
| Is it safe to retry a request that may have already succeeded? | PARAPHRASE | tech-idempotency | **tech-http-429 (0.553)** | **2** | loan-prepayment (wrong) |
| Can I return a product if I no longer want it? | KEYWORD_TRAP | faq-returns | faq-returns (0.570) | 1 | java-garbage-collection (wrong) |
| I forgot my login credentials | PARAPHRASE | faq-password-reset | faq-password-reset (0.587) | 1 | faq-password-reset (ok) |

**Summary:** vector search hit@1 **14/16**, hit@3 **16/16**. The naive keyword baseline hit@1 was 11/16.
On the 8 paraphrase queries: vector **6/8**, keyword 4/8.

What to learn from it:

- **Paraphrases are where embeddings shine.** "close relative passes away" found *emergency leave* with no
  shared keyword; "send back something I bought" found *returns*.
- **Keywords fail in surprising ways.** "Can I return a product if I no longer want it?" matched *garbage collection*
  on the words "no longer".
- **Small models blur neighbouring concepts.** Both misses (q09, q14) put the right document at rank 2 behind a
  document from the same topic (loans, HTTP retries). Top-K > 1 matters, and later a re-ranker or a stronger
  model can fix the order.
- **Scores are not probabilities.** 0.35 was a correct match (coffee) and 0.57 a wrong one (prepayment).
  Pick thresholds per model, from data.
- The keyword traps were partly *passed* by the naive baseline because other words also overlapped. Real keyword
  engines (BM25) behave differently; we only use the baseline to show the *kind* of mistakes each approach makes.

## Gotchas found while building this

1. **Threshold 0.0 is not "everything".** Spring AI's accept-all threshold becomes `WHERE distance < 1`, so documents
   with negative cosine similarity are never returned. Example: query *"How do I leave feedback on something I purchased?"*
   with filter `department == 'HR'` and `topK: 3` returned **1** result, because the other HR documents had similarity
   -0.08 to -0.12.
2. **Spring AI's default ONNX model cache is a new temp directory on every start**, which re-downloads 90 MB each time.
   `application.yml` sets a stable cache directory.
3. **Table name and dimension must change together with the model.** The app enforces this at startup.

## Production considerations (pgvector-specific)

- Tune `hnsw.ef_search` (default 40) per query when you need higher recall; enable `hnsw.iterative_scan` when filters are selective.
- Build HNSW indexes after bulk loads, and give `maintenance_work_mem` enough memory to keep the build in RAM.
- Managed PostgreSQL (RDS, Cloud SQL, Azure, Supabase, Neon) supports pgvector, but check the available version.
- Vector rows are large (about 1.5 KB at 384-d, about 6 KB at 1536-d). Plan storage, `VACUUM`, and backup size.
- Use a dedicated DB role with least privilege. `CREATE EXTENSION` usually needs elevated rights, so do it once, outside the app.
