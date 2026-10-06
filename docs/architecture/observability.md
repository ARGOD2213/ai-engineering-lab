# Observability

Spring Boot Actuator + Micrometer. Spring AI instruments `EmbeddingModel` and `VectorStore` itself, so
embedding latency and vector-store latency are measured separately with no custom code.

## Endpoints

| Endpoint | What you get |
|---|---|
| `GET /actuator/health` | app status + database connectivity (`components.db`) |
| `GET /actuator/info` | active embedding provider, model, dimensions, vector table, distance and index type |
| `GET /actuator/metrics` | list of metric names |
| `GET /actuator/metrics/{name}` | values and tags of one metric |

## Metrics

| Metric | Source | Answers |
|---|---|---|
| `http.server.requests` | Spring Boot | request count, latency and errors per endpoint and status (`uri`, `status`, `outcome` tags) |
| `gen_ai.client.operation` | Spring AI | **embedding latency** (`gen_ai.operation.name=embedding`, `gen_ai.system=onnx` or `openai`) |
| `db.vector.client.operation` | Spring AI | **vector store latency** per operation (`db.operation.name=add` or `query`, `db.system=pg_vector`) |
| `lab.search.results` | this service | **result count** per search (distribution summary) |
| `lab.documents.ingested` | this service | documents stored |
| `lab.api.errors` | this service | errors by `type` (validation, invalid_request, not_found, embedding_provider, vector_store, unexpected) |

```bash
curl -s localhost:8080/actuator/metrics/gen_ai.client.operation
curl -s 'localhost:8080/actuator/metrics/db.vector.client.operation?tag=db.operation.name:query'
curl -s localhost:8080/actuator/metrics/lab.search.results
```

Search latency = embedding the query (`gen_ai.client.operation`) + pgvector (`db.vector.client.operation`).
Comparing the two shows whether the model or the database is the bottleneck. With a hosted API it is almost always the model.

## Logs

One INFO line per operation, with sizes and timings but never content:

```text
Generated embedding: chars=56 dimensions=384 latencyMs=9
Stored documents: count=30 totalChars=4123 model=sentence-transformers/all-MiniLM-L6-v2 latencyMs=161
Semantic search: queryChars=62 topK=3 threshold=0.0 filtered=false results=3 topScore=0.5533 latencyMs=15
Embedding model verified: provider=transformers-onnx (local, in-process) model=... dimensions=384 probeLatencyMs=...
```

Provider and database failures are logged at WARN with the exception *type* only. Details go to DEBUG.

## Next steps (not in milestone 1)

- Prometheus registry + Grafana dashboard
- Distributed tracing (OpenTelemetry) to see the embedding span inside the search span
- Alerting on `lab.api.errors{type=embedding_provider}` and p95 search latency
