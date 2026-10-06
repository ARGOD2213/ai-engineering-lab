# 03 - Retrieval-Augmented Generation (RAG)

**Status: not started (milestone 3).**

RAG = the semantic search built in milestone 1 + an LLM that answers using the retrieved text.

Planned scope when we get here:

- `ingestion/` - loading real documents, chunking strategies, chunk size vs. embedding token limits
- `retrieval/` - top-K tuning, similarity thresholds, metadata filters, re-ranking
- `rag-api/` - prompt assembly, answer generation, citing sources

Prerequisite: understand milestone 1 (`01-embeddings`, `02-vector-databases`) first.
The retrieval half of RAG already exists: `POST /api/search` in `services/ai-engineering-api`.
