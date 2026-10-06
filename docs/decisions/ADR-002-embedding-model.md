# ADR-002: Local all-MiniLM-L6-v2 by default, OpenAI text-embedding-3-small as opt-in

- **Status:** accepted
- **Date:** 2026-10-06
- **Milestone:** 1

## Context

The lab needs a real, existing embedding model through a provider supported by Spring AI. It must be reproducible on
any developer machine after a `git clone`, must not require paid API calls for tests, and must never hard-code credentials.

## Decision

1. **Default:** `sentence-transformers/all-MiniLM-L6-v2` (384 dimensions) through `spring-ai-starter-model-transformers`
   (ONNX Runtime, in the JVM). No API key. The model (~90 MB) is downloaded once and cached in `~/.cache/ai-engineering-lab/onnx`.
2. **Opt-in:** OpenAI `text-embedding-3-small` (1536 dimensions) through `spring-ai-starter-model-openai`, activated by
   `EMBEDDING_PROVIDER=openai` and `OPENAI_API_KEY`. It writes to its own table.
3. The active model is selected with `spring.ai.model.embedding`. All chat, image, audio and moderation models are set to `none`.
4. A startup check embeds a probe text and fails if the real dimension differs from the configured one.

## Why

- Anyone can run the full lab for free and offline after the first download. No data leaves the machine.
- Tests are deterministic and free: unit tests mock the model; integration tests use a fake hashing model; real-model
  tests are opt-in (`-Preal-model`).
- Having two providers with *different dimensions* makes the "models are not interchangeable / re-embed on switch"
  lesson concrete instead of theoretical.

## Consequences

- (+) Zero cost, low latency (about 13 ms per short text on 4 vCPUs), privacy-friendly.
- (−) Lower retrieval quality than large hosted models (see the q09/q14 misses in the evaluation).
- (−) **128-token input limit** with Spring AI's bundled tokenizer: longer text is silently truncated.
- (−) Larger runtime: native ONNX Runtime and tokenizer libraries make the jar ~280 MB; DJL downloads PyTorch native
  libraries on first use for the pooling step; about 740 MB resident memory.
- (−) The OpenAI path is tested against a local OpenAI-compatible stub (`OpenAiProfileWiringTest`), not against the real API:
  the environment where this was built had no network access to api.openai.com and no key. Verify it once with your own key.

## Alternatives

| Option | Why not default |
|---|---|
| OpenAI `text-embedding-3-small` | best quality/price among hosted options, but needs a key, costs money, sends data to a third party |
| OpenAI `text-embedding-3-large` (3072 d) | higher quality and cost; exceeds pgvector's 2000-dimension HNSW limit for `vector` (needs `halfvec` or reduced dimensions) |
| Ollama + `nomic-embed-text` (768 d) | free and local, but requires installing and running Ollama as an extra service |
