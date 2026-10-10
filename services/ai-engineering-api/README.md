# ai-engineering-api

Spring Boot service for milestone 1: **text → embedding → pgvector → semantic search**. No LLM.

| | |
|---|---|
| Java | 21 |
| Build | Maven (wrapper included: `./mvnw`, `mvnw.cmd`) |
| Spring Boot | 4.1.1 (Web MVC, Validation, Actuator, JDBC, Flyway) |
| Spring AI | 2.0.1 (`EmbeddingModel`, `VectorStore`/`PgVectorStore`, `Document`, `SearchRequest`) |
| Embedding model | all-MiniLM-L6-v2 (local ONNX, 384-d) by default; OpenAI text-embedding-3-small (1536-d) opt-in |
| Database | PostgreSQL 17 + pgvector 0.8.7 (Docker) |

## Run

From the repository root:

```bash
cp .env.example .env                  # once; set POSTGRES_PASSWORD
docker compose up -d                  # PostgreSQL + pgvector
cd services/ai-engineering-api
./mvnw spring-boot:run                # Windows: mvnw.cmd spring-boot:run
```

The **first** start downloads the embedding model (~90 MB) into `~/.cache/ai-engineering-lab/onnx`. DJL also
downloads PyTorch native libraries into `~/.djl.ai` the first time an embedding is computed. Later starts take about 10 s.

Startup log lines worth reading:

```text
Successfully applied 3 migrations ...                       # Flyway created the vector tables
PG VectorStore schema validation successful                 # Spring AI agrees with the schema
Embedding model verified: ... dimensions=384 ...            # the model really returns 384 numbers
Tomcat started on port 8080
```

### Use OpenAI instead of the local model

```bash
# in .env
EMBEDDING_PROVIDER=openai
OPENAI_API_KEY=sk-...        # never commit this
```

This switches the `EmbeddingModel` bean, the dimension (1536) and the table (`documents_openai_text_embedding_3_small`).
Documents stored with the local model are **not** visible in the OpenAI table. Load them again, which re-embeds them.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/embeddings` | text → `{text, model, dimensions, embedding[]}` (nothing stored) |
| POST | `/api/embeddings/similarity` | 2–10 texts → pairwise cosine similarity + keyword overlap |
| POST | `/api/documents` | store one document (text + metadata + vector); `201` + `Location` |
| POST | `/api/documents/batch` | store up to 100 documents with one batched embedding call |
| GET | `/api/documents/{id}?includeEmbedding=true` | read a stored row back (vector size from `vector_dims`, `createdAt`) |
| POST | `/api/search` | `{query, topK?, similarityThreshold?, filterExpression?}` → top-K documents with score |
| GET | `/actuator/health`, `/actuator/info`, `/actuator/metrics` | operations |

Errors use RFC 9457 problem details (`application/problem+json`):

| Status | When |
|---|---|
| 400 | validation failure (field errors in `errors`), malformed JSON, invalid `filterExpression`, null metadata values |
| 404 | unknown document id |
| 503 | embedding provider or vector store unavailable (generic message; details only in server logs) |

Request collections: [`requests.http`](requests.http) (IntelliJ / VS Code) and
[`postman/`](../../postman/ai-engineering-lab.postman_collection.json).

### Examples

```bash
curl -s -X POST localhost:8080/api/embeddings -H 'Content-Type: application/json' \
  -d '{"text": "Employees are entitled to 12 casual leave days per year."}'

curl -s -X POST localhost:8080/api/documents -H 'Content-Type: application/json' \
  -d '{"text": "Employees can take up to 5 days of emergency leave.",
       "metadata": {"department": "HR", "documentType": "POLICY"}}'

curl -s -X POST localhost:8080/api/search -H 'Content-Type: application/json' \
  -d '{"query": "How many emergency leave days can an employee take?", "topK": 5}'

curl -s -X POST localhost:8080/api/search -H 'Content-Type: application/json' \
  -d '{"query": "leave", "topK": 5, "filterExpression": "department == '\''HR'\'' && documentType == '\''POLICY'\''"}'
```

## Employee API (plain CRUD)

A conventional controller → service → repository → PostgreSQL resource in `com.aiengineeringlab.api.employee`
(JPA/Hibernate; table created by Flyway `V5__create_employees.sql`). It is the baseline for later exercises.

| Method | Path | Success | Errors |
|---|---|---|---|
| POST | `/api/employees` | `201` + `Location` + body | `400` invalid body |
| GET | `/api/employees` | `200` list | |
| GET | `/api/employees/{id}` | `200` | `404` unknown id |
| PUT | `/api/employees/{id}` | `200` updated employee | `400` invalid body, `404` unknown id |
| DELETE | `/api/employees/{id}` | `204` | `404` unknown id |

Body: `{"name": "Asha", "department": "Engineering", "email": "asha@example.com"}` (all three required, `email` must be valid).
Ready-made calls are in [`requests.http`](requests.http) (section "Employee API").

## Tests

```bash
./mvnw test                # 53 tests: unit (mocked model) + integration (Testcontainers pgvector, fake model, OpenAI stub)
./mvnw test -Preal-model   # 4 tests with the REAL model: similarity experiment + retrieval evaluation
```

| Suite | Needs | Covers |
|---|---|---|
| Unit (`service`, `controller`, `configuration`) | nothing | embedding service, ingestion, search, validation, error mapping, dimension check, cosine maths |
| Integration (`PgVectorIntegrationTest`) | Docker | Flyway schema, vector storage and `vector_dims`, upsert, batching, ordering, top-K, metadata filter, wrong-dimension rejection, actuator |
| Integration (`OpenAiProfileWiringTest`) | Docker | the `openai` profile against an in-process server speaking the OpenAI embeddings wire format: model selection, API key header, 1536-d, own table, batching |
| Real model (`-Preal-model`) | Docker + model download | A/B/C similarity, 128-token truncation, retrieval hit@1/hit@3 vs. keyword baseline |

No test calls a paid API. The integration tests use `HashingEmbeddingModel`, a deterministic keyword-hashing fake, or a local OpenAI-compatible stub.
They are skipped automatically when Docker is not available.

## Configuration

| Variable | Default | Meaning |
|---|---|---|
| `EMBEDDING_PROVIDER` | `local` | `local` (ONNX MiniLM) or `openai` |
| `OPENAI_API_KEY` | (none) | required only for `openai` |
| `EMBEDDING_MODEL_CACHE_DIR` | `~/.cache/ai-engineering-lab/onnx` | ONNX model cache |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` | `localhost` / `5433` / `ai_lab` | database location |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `ai_lab` / (none) | credentials (password has no default) |
| `SERVER_PORT` | `8080` | HTTP port |

Values come from real environment variables or from the repository's `.env` file (imported by
`spring.config.import`). Real environment variables take precedence.

## Code map

```text
src/main/java/com/aiengineeringlab/api/
├── employee/        Employee (entity), EmployeeRepository, EmployeeService, EmployeeController
├── controller/      EmbeddingController, DocumentController, SearchController, dto/ (validated requests)
├── service/         EmbeddingService, DocumentIngestionService, SemanticSearchService, TextSimilarity
├── repository/      DocumentRepository (read side, plain SQL)
├── domain/          records: EmbeddingResult, StoredDocument, SearchResult, ...
├── configuration/   EmbeddingProperties, EmbeddingDimensionVerifier, EmbeddingInfoContributor
└── exception/       ApiExceptionHandler (+ exception types)
src/main/resources/
├── application.yml            local model defaults
├── application-openai.yml     OpenAI profile
└── db/migration/              V1 extension, V2 MiniLM table, V3 OpenAI table, V5 employees
```

## Troubleshooting

| Symptom | Fix |
|---|---|
| `Connection refused` on 5433 | `docker compose up -d` and wait for `healthy`; check `POSTGRES_PORT` |
| `password authentication failed` | `.env` password differs from the one the volume was created with → `docker compose down -v` (deletes data) |
| `Embedding model ... produced N dimensions but the service is configured for M` | model and table do not match; check `EMBEDDING_PROVIDER` and `lab.embedding.dimensions` |
| first start hangs at "Caching the URL ..." | the model is downloading (~90 MB) |
| `EngineException` / download error from `ai.djl` on first embedding | DJL could not download PyTorch natives from `publish.djl.ai`; allow that host, or add the `ai.djl.pytorch:pytorch-native-cpu` and `pytorch-jni` jars for your OS |
| Tests skipped | Docker is not running (integration tests are skipped without Docker) |
