# 01 - Embeddings

> Text → **embedding model** → vector of numbers that represents meaning.

## What

An embedding is a fixed-length array of floating-point numbers produced by an embedding model.
Texts with similar meaning get vectors that point in similar directions. Here is the default model of this lab:

```text
"Employees are entitled to 12 casual leave days per year."
        │  all-MiniLM-L6-v2
        ▼
[0.2827, 0.0836, 0.2842, -0.0229, 0.1210, ... 384 numbers in total]
```

You cannot read meaning from a single number. Meaning only shows up when you **compare** vectors,
usually with cosine similarity.

## Why

Keyword search matches spelling. Embeddings match meaning:

- "Workers are entitled to twelve days of casual leave annually" ≈ "Employees receive 12 casual leave days every year" (cosine 0.889)
- "beans" in a Spring question vs. "coffee beans" share a word but not a meaning

Embeddings are the foundation of semantic search (milestone 1) and of the retrieval step in RAG (milestone 3).

## How (in this lab)

| What | Where |
|---|---|
| Text → vector | `POST /api/embeddings`, served by `EmbeddingService` |
| Compare texts | `POST /api/embeddings/similarity`, using `TextSimilarity.cosineSimilarity` |
| Experiment | [`experiments/semantic-similarity`](experiments/semantic-similarity/README.md) |
| Short "why" answers | [`notes/developer-notes.md`](notes/developer-notes.md) |

The code depends only on Spring AI's `EmbeddingModel` interface. The concrete model is chosen by configuration.

## Model choice

| | **Default: local** | **Opt-in: OpenAI** |
|---|---|---|
| Model | `sentence-transformers/all-MiniLM-L6-v2` | `text-embedding-3-small` |
| Provider (Spring AI) | `spring-ai-starter-model-transformers` (ONNX Runtime, in-process) | `spring-ai-starter-model-openai` (official OpenAI Java SDK) |
| Dimensions | **384** (measured at startup) | **1536** (default output size) |
| Activate | default | `EMBEDDING_PROVIDER=openai` + `OPENAI_API_KEY` |
| pgvector table | `documents_minilm_l6_v2` | `documents_openai_text_embedding_3_small` |
| Cost | free (your CPU) | paid per token |
| Network | downloads the model once, then runs offline | every call goes to the API |
| Max input | **128 tokens** (Spring AI's bundled tokenizer truncates silently) | 8191 tokens |
| Languages | mainly English | multilingual |
| Verified in this repo | yes, end-to-end with the real model | end-to-end against an OpenAI-compatible stub (`OpenAiProfileWiringTest`), not against the real API |

Why the local model is the default: anyone can clone the repository and run everything with no API key,
no cost, and no data leaving the machine. It is also fast enough to make experimenting pleasant.
See [ADR-002](../docs/decisions/ADR-002-embedding-model.md).

## Vector dimensions

- The model decides the dimension; you do not choose it per request. MiniLM always returns 384 numbers.
- The pgvector column is declared with that exact size: `embedding vector(384)`. PostgreSQL rejects any
  other size: `expected 384 dimensions, not 1536` (an integration test proves this).
- `EmbeddingDimensionVerifier` embeds a probe text at startup and refuses to start if the model's real
  output size differs from `lab.embedding.dimensions`. The API reports the size of the vector it actually
  received, not a configured constant.
- More dimensions do not automatically mean better results. They mean more storage
  (384 × 4 bytes ≈ 1.5 KB per vector; 1536 × 4 bytes ≈ 6 KB) and slower distance computations.
  `text-embedding-3-*` models can return shortened vectors through a `dimensions` parameter, and the table must match whatever you pick.

## Cost considerations

- **Local model:** no per-call cost. You pay in CPU and RAM (measured: about 740 MB resident memory for the whole
  service, of which only about 165 MB is JVM heap and non-heap; the rest is native ONNX Runtime and PyTorch memory),
  a one-time ~90 MB model download, and native libraries (ONNX Runtime and, on first use, PyTorch libraries that
  DJL downloads for the pooling step).
- **OpenAI `text-embedding-3-small`:** billed per input token. OpenAI's list price at launch (January 2024)
  was **$0.02 per 1M tokens**. Always check the current pricing page.
  - 1 token ≈ ¾ of an English word. Our 30-document dataset is ≈ 1,000 tokens, a fraction of a cent.
  - 1M documents × 500 tokens = 500M tokens ≈ $10 at that price. Every query is embedded too.
  - **Re-embedding costs the same as the first embedding**, so a model switch means re-paying for the whole corpus.
- Batching (`POST /api/documents/batch`) does not lower the per-token price. It does cut request
  overhead and latency, and providers often offer a cheaper asynchronous batch API.

## Latency considerations

Measured in this repository on a 4-vCPU cloud VM (Intel Xeon 2.1 GHz), local model, warm JVM:

| Operation | Median | p95 |
|---|---|---|
| `POST /api/embeddings` (one short sentence, full HTTP round trip) | 12.8 ms | 20.4 ms |
| `POST /api/search` (embed query + pgvector top-5, full HTTP round trip) | 12.6 ms | 17.6 ms |
| `POST /api/documents/batch`, 30 documents | 173 ms total | n/a |

- The **first** start downloads the model (seconds to minutes depending on bandwidth). Later starts load it from
  `~/.cache/ai-engineering-lab/onnx` in about 3 s.
- A hosted API adds a network round trip to **every** ingestion and **every** query. This was not measured here,
  because the sandbox could not reach api.openai.com. Expect roughly 100–500 ms per call depending on region and load,
  plus occasional rate limiting (HTTP 429). Search latency is then dominated by the embedding call, not by pgvector.
- Watch `gen_ai.client.operation` (embedding time) and `db.vector.client.operation` (vector store time) on
  `/actuator/metrics` to see where time goes.

## Model limitations (all-MiniLM-L6-v2 through Spring AI)

1. **128-token truncation.** Text beyond about 90–100 words is silently ignored. The experiment shows a
   cosine of 1.0000 between a long text and the same text plus an extra sentence. Long documents need chunking (milestone 3).
2. **Small model (22M parameters).** It confuses related concepts in the same domain. In the
   [retrieval evaluation](../02-vector-databases/pgvector/README.md#experiment-results), "borrower stops paying instalments"
   ranked *loan prepayment* above *loan default*.
3. **English-centric.** Use a multilingual model for other languages.
4. **Vectors are not normalised** (mean pooling, L2 norm ≈ 6.7). Use cosine distance, not inner product.
5. **Negation and numbers are weak spots** of most embedding models: "12 days" vs "15 days" look almost identical.

## Why changing the embedding model requires re-embedding

Every model learns its own coordinate system. Dimension 17 of MiniLM has nothing to do with dimension 17 of
OpenAI's model. So:

- vectors from different models are **not comparable**, even if they happen to have the same size;
- a query must be embedded with the **same model** that embedded the documents;
- switching models therefore means re-embedding every stored document. Our design makes this explicit:
  **one table per model**, selected by configuration, plus a startup check on the dimension.

In production this becomes a migration: build the new table in the background, run both side by side,
compare retrieval quality, then switch reads and drop the old table.

## Production considerations

- Pin the model version (a silently updated model is also a "different model").
- Store which model produced each vector. Here the table name encodes it; larger systems add a `model` column.
- Cap input size (the API rejects texts over 8,000 characters) and plan chunking for long texts.
- Treat the provider as an unreliable dependency: timeouts, retries with backoff on 429/5xx, circuit breaking.
- Never log the text being embedded. It is user data. This service logs sizes and timings only.
- Sending text to a hosted API means sending data to a third party. Check data-processing terms before embedding personal or confidential data.

## Interview questions

See [docs/interview/embeddings.md](../docs/interview/embeddings.md).
