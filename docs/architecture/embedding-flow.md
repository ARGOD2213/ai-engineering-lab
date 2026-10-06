# Embedding and ingestion flow

## 1. Generate an embedding (nothing stored)

`POST /api/embeddings`

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as EmbeddingController
    participant S as EmbeddingService
    participant M as EmbeddingModel<br/>(all-MiniLM-L6-v2)

    C->>API: {"text": "Employees are entitled to 12 casual leave days per year."}
    API->>API: Bean Validation (not blank, ≤ 8000 chars)
    API->>S: embed(text)
    S->>M: embed(text)
    Note over M: tokenize (max 128 tokens)<br/>→ ONNX transformer<br/>→ mean pooling
    M-->>S: float[384]
    S-->>API: EmbeddingResult(text, model, dimensions = vector.length, embedding)
    API-->>C: 200 {"text", "model", "dimensions": 384, "embedding": [...]}
```

`dimensions` is the length of the vector the model actually returned, not a configured constant.

## 2. Ingest a document (text + metadata + vector → pgvector)

`POST /api/documents` (single) or `POST /api/documents/batch` (one batched model call)

```mermaid
flowchart LR
    A[REST API] --> B[Validation]
    B --> C["DocumentIngestionService<br/>builds Spring AI Document<br/>(id, text, metadata)"]
    C --> D["VectorStore.add()"]
    D --> E["EmbeddingModel<br/>text → float[384]"]
    E --> F["PgVectorStore<br/>INSERT … ON CONFLICT (id) DO UPDATE"]
    F --> G[("pgvector<br/>content · metadata · embedding · created_at")]
    G --> H["DocumentRepository<br/>reads row back: vector_dims, created_at"]
    H --> I["201 Created + Location"]
```

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as DocumentController
    participant S as DocumentIngestionService
    participant VS as VectorStore (PgVectorStore)
    participant M as EmbeddingModel
    participant DB as PostgreSQL + pgvector
    participant R as DocumentRepository

    C->>API: {"text": "...", "metadata": {"department": "HR"}}
    API->>API: validate (text, id pattern, ≤ 20 metadata entries)
    API->>S: ingest(NewDocument)
    S->>VS: add([Document])
    VS->>M: embed(texts) (batched)
    M-->>VS: [float[384]]
    VS->>DB: INSERT (id, content, metadata::jsonb, embedding)
    S->>R: findById(id)
    R->>DB: SELECT …, vector_dims(embedding), created_at
    DB-->>R: row
    R-->>S: StoredDocument
    S-->>API: StoredDocument
    API-->>C: 201 {"id", "text", "metadata", "embeddingDimensions": 384, "createdAt"}
```

## Key points

- **Our service never calls the embedding model during ingestion.** `VectorStore.add()` does it. The vector store
  abstraction is "embedding-aware"; the database is not.
- **Upsert by id.** Re-sending the same id replaces text, metadata and vector, and `created_at` keeps its first value.
  This makes the sample dataset safe to load repeatedly.
- **Failure mapping:** a database error → `503 Vector store unavailable`; anything else during `add()` comes from
  the embedding step → `503 Embedding provider unavailable`. Clients never see provider messages.
- **What is logged:** count, total characters, model, latency. Never the text.
