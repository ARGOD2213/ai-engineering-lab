# RAG learning progress

Rule: a lesson is ticked only after the teach-back passes, tests are green, and notes/lessonN.md is written.

- [x] Lesson 0: Orientation (API -> model flow, 384-value vector, Spring bean/provider distinction, vector storage concepts; DB-down exercise reasoned about but not run)
- [ ] Lesson 1: Discover why we chunk (cosine + prefix-length experiment)
- [ ] Lesson 2: Sentence-aware chunker, test first
- [ ] Lesson 3: Tokens for real (3A count tokens, 3B WordPiece)
- [ ] Lesson 4: EmbeddingClient abstraction and batching
- [ ] Lesson 5: pgvector storage (shopnest_chunks, ingestion)
- [ ] Lesson 6: Retrieval endpoint, no LLM yet (hit@3 baseline)
- [ ] Lesson 7: Generation (LlmClient, PromptBuilder, /api/rag/ask)
- [ ] Lesson 8: Evaluate and tune
- [ ] Lesson 9: Spring hardening (config, errors, retry, cache, metrics)
- [ ] Lesson 10: Port to Spring AI abstractions and decide (ADR)

## Log
- 2026-10-08: setup done, repo inspected. Branch `rag-learning`. Starting Lesson 0.
- 2026-10-09: Lesson 0 closed after successful `/api/rag/embed` curl, teach-back, and quiz. The database-down break exercise was discussed but not executed; see [lesson0.md](./lesson0.md).
