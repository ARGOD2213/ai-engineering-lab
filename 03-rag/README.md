# 03 - Retrieval-Augmented Generation (RAG)

**Learning status: in progress — Lesson 0 (Orientation) completed.**

RAG = the semantic search built in milestone 1 + an LLM that answers using the retrieved text.

This learning track builds the pipeline step by step in `services/ai-engineering-api/src/main/java/com/aiengineeringlab/api/rag`.
The learner writes the implementation; see [notes/progress.md](./notes/progress.md) for lesson status and
[notes/lesson0.md](./notes/lesson0.md) for the first lesson recap.

Planned learning scope:

- `ingestion/` - loading real documents, chunking strategies, chunk size vs. embedding token limits
- `retrieval/` - top-K tuning, similarity thresholds, metadata filters, re-ranking
- `rag-api/` - prompt assembly, answer generation, citing sources

The existing embedding and vector-search milestone is available for exploration, but the RAG learning implementation is kept separate.
