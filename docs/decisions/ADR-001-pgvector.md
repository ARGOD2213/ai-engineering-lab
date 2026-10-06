# ADR-001: PostgreSQL + pgvector as the first vector store

- **Status:** accepted
- **Date:** 2026-10-06
- **Milestone:** 1

## Context

Milestone 1 needs somewhere to store embeddings and run nearest-neighbour search. Candidates: pgvector, Chroma, Qdrant,
Redis Vector Search (all supported by Spring AI). The goal is learning, with production habits from day one.

## Decision

Use **PostgreSQL 17 + pgvector 0.8.7** (`pgvector/pgvector:0.8.7-pg17`), accessed through Spring AI's `PgVectorStore`.

- One table per embedding model (`documents_minilm_l6_v2` = `vector(384)`, `documents_openai_text_embedding_3_small` = `vector(1536)`).
- **Schema owned by Flyway** (`initialize-schema: false`, `schema-validation: true`). Spring AI validates; it does not create.
- **Cosine distance** (`<=>`) with an **HNSW** index (`vector_cosine_ops`), a **JSONB** metadata column with a GIN index,
  `TEXT` ids, and a `created_at` column that we add ourselves.

## Why

- **Familiar:** a Java/Spring backend engineer already knows PostgreSQL. Transactions, SQL, backups and Flyway all still apply.
- **Transparent:** everything is inspectable with `psql` (`vector_dims`, `EXPLAIN`, `<=>`). This is ideal for learning what a vector DB actually does.
- **One system:** vectors live next to business data, so there is no second datastore to run, secure and back up.
- **Production-credible:** available on every major managed PostgreSQL service; HNSW is good enough for millions of vectors.

## Consequences

- (+) Simple local setup: one container.
- (+) Explicit schema makes the dimension/model coupling visible.
- (−) Scaling to hundreds of millions of vectors or very high QPS needs care (memory for HNSW, partitioning). Dedicated
  vector DBs may scale more easily there.
- (−) Filtered ANN queries can return fewer than K results unless iterative scans are tuned.
- (−) `CREATE EXTENSION vector` needs elevated privileges; in production it is done once by a DBA or migration role.

## Alternatives (to be explored in milestone 2 with the same dataset)

| Store | Why not first |
|---|---|
| Qdrant | purpose-built and fast, but a new system to learn and operate before the concepts are clear |
| Chroma | very easy to start, less common in Java production stacks |
| Redis | fast in-memory, but RAM cost and index schema rules add concepts that are unrelated to embeddings |
