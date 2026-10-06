# Security: local development vs. production

Milestone 1 has no authentication on purpose. It is a local learning service. The habits below are already in place so
nothing has to be "made secure later".

## Already enforced in this repository

| Habit | Where |
|---|---|
| No secrets in git: `.env` is git-ignored, only `.env.example` (no real values) is committed | `.gitignore`, `.env.example` |
| No default DB password: Compose refuses to start without `POSTGRES_PASSWORD`; the app has no fallback password | `docker-compose.yml`, `application.yml` |
| API keys only from the environment: `OPENAI_API_KEY` is read from env or `.env`, never hard-coded | `application-openai.yml` |
| Database bound to localhost only (`127.0.0.1:5433`) | `docker-compose.yml` |
| No user text in logs: logs contain sizes, counts, latencies and ids, not text or queries | services, `ApiExceptionHandler` |
| No provider error messages to clients: provider messages can include request details or masked keys; clients get a generic 503 | `ApiExceptionHandler` |
| No stack traces or exception messages in HTTP errors | `server.error.include-*: never`, RFC 9457 problem details |
| Spring AI observability does not record query/response content | `spring.ai.vectorstore.observations.log-query-response: false` |
| Input limits: text ≤ 8,000 chars, query ≤ 2,000, topK ≤ 20, batch ≤ 100, metadata ≤ 20 entries, id pattern | request DTOs |
| SQL identifiers from configuration are validated before being concatenated | `DocumentRepository` |
| Actuator exposes only `health`, `info`, `metrics`; `/actuator/info` shows model and table, never credentials | `application.yml`, `EmbeddingInfoContributor` |

## Local development (acceptable here, not in production)

- No authentication or authorization on any endpoint.
- `management.endpoint.health.show-details: always`.
- The database superuser is also the application user (it can run `CREATE EXTENSION`).
- Plain HTTP.
- `.env` file on disk.

## Production checklist (when this grows up)

- **Secrets:** a secret manager (Vault, AWS Secrets Manager, Kubernetes secrets with encryption at rest), not `.env`. Rotate keys.
- **AuthN/AuthZ:** OAuth2/JWT on the API; per-tenant or per-role filters applied *server-side* to every vector search
  (never trust a client-supplied `filterExpression` for access control).
- **Database:** a least-privilege app role (no superuser, no DDL at runtime); extensions installed by a DBA or migration
  role; TLS to the database; private network only.
- **Actuator:** separate management port, `show-details: when-authorized`, no public exposure.
- **Data governance:** sending text to a hosted embedding API is sending data to a third party. Check DPA/retention terms;
  keep sensitive data on a local or self-hosted model if required. Embeddings can leak information about the
  source text, so treat vectors as sensitive as the text itself.
- **Abuse protection:** rate limiting per client (embedding calls cost money), request size limits at the gateway,
  timeouts and circuit breakers toward the provider.
- **Deletion:** a "delete my data" request must delete the text *and* the vector *and* any cached copies.
- **Supply chain:** pin model artefacts (and verify checksums) instead of downloading "latest" at startup.
