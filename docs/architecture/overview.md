# Architecture overview (milestone 1)

A single Spring Boot service (modular monolith) plus one PostgreSQL + pgvector container. No LLM.

## The 10-second version (interview whiteboard)

```mermaid
flowchart LR
    A[Client] --> B[Spring Boot REST API] --> C[Embedding Service] --> D[Embedding Model] --> E[VectorStore] --> F[(PGVector)]
```

```mermaid
flowchart LR
    Q[User Query] --> E[Embedding] --> V[Query Vector] --> S[PGVector Similarity Search] --> K[Top-K Documents]
```

## Components

```mermaid
flowchart TB
    client["Client<br/>(curl, Postman, tests)"]

    subgraph app["ai-engineering-api (Spring Boot 4.1, Java 21)"]
        direction TB
        controllers["Controllers<br/>EmbeddingController · DocumentController · SearchController"]
        services["Services<br/>EmbeddingService · DocumentIngestionService · SemanticSearchService"]
        subgraph springai["Spring AI 2.0"]
            embeddingModel["EmbeddingModel<br/>(all-MiniLM-L6-v2 via ONNX, or OpenAI)"]
            vectorStore["VectorStore<br/>(PgVectorStore)"]
        end
        repository["DocumentRepository<br/>(JdbcClient, read side)"]
    end

    subgraph db["PostgreSQL 17 + pgvector 0.8.7 (Docker)"]
        table[("documents_minilm_l6_v2<br/>id · content · metadata JSONB · embedding vector(384) · created_at")]
    end

    client -->|HTTP/JSON| controllers --> services
    services --> embeddingModel
    services --> vectorStore
    services --> repository
    vectorStore -->|embeds text via| embeddingModel
    vectorStore -->|SQL: INSERT / ORDER BY embedding #lt;=#gt; ?| table
    repository -->|SQL: vector_dims, created_at| table
```

## Who is responsible for what

```text
Spring Boot ─▶ Spring AI ─▶ EmbeddingModel ─▶ VectorStore ─▶ pgvector
```

| Component | Responsibility | Does NOT do |
|---|---|---|
| **Spring Boot** | HTTP, validation, configuration, dependency injection, Actuator, Flyway | anything AI-specific |
| **Spring AI** | portable abstractions (`EmbeddingModel`, `VectorStore`, `Document`, `SearchRequest`, filter expressions) and auto-configuration that picks the implementation from properties | hold data or run models itself |
| **EmbeddingModel** | text → `float[]`. The *only* component that understands language | store anything |
| **VectorStore** (`PgVectorStore`) | glue: calls the `EmbeddingModel`, writes text + metadata + vector, translates `SearchRequest` and filters into SQL, maps rows back into `Document`s with a score | understand meaning, or choose the model |
| **pgvector** | stores `vector(384)`, computes distances (`<=>`), indexes them (HNSW) | create vectors or know what they mean |
| **Our code** | API contract, validation, error mapping, metrics, logging policy, reading stored rows back | depend on a concrete model or store in the service layer |

## Packages

```text
com.aiengineeringlab.api
├── controller/        HTTP endpoints + request DTOs (validation)
├── service/           use cases: embed, compare, ingest, search
├── repository/        plain-SQL read access to the vector table
├── domain/            records returned by services (and serialised as responses)
├── configuration/     embedding properties, startup dimension check, /actuator/info
└── exception/         exception types + RFC 9457 problem-detail mapping
```

## Flows

- [Embedding and ingestion flow](embedding-flow.md)
- [Vector search flow](vector-search-flow.md)
- [Security: local vs. production](security.md)
- [Observability](observability.md)
