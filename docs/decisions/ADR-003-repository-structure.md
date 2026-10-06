# ADR-003: Repository structure: learning modules + one modular-monolith service

- **Status:** accepted
- **Date:** 2026-10-06

## Decision

```text
ai-engineering-lab/
├── 01-embeddings/          learning notes + experiments (what/why), no code duplication
├── 02-vector-databases/    shared dataset, scripts, one folder per store
├── 03-rag/ 04-ai-agents/ 05-mcp/   future milestones, intentionally empty
├── docs/                   architecture, ADRs, interview prep, weekly notes
├── infrastructure/docker/  files mounted by Compose (init SQL, verification script)
├── postman/                API collection
├── services/ai-engineering-api/    the only deployable: Spring Boot modular monolith
└── docker-compose.yml      at the root, so `docker compose up -d` works from the repo root and reads `.env`
```

## Why

- **One service, not microservices.** Every milestone (embeddings, vector stores, RAG, agents) is a *module* (package)
  of the same application. Splitting into services adds network, deployment and consistency problems that teach
  nothing about AI engineering.
- **Learning folders hold explanations and experiments, not code.** Code lives in one place, so the docs point to it.
- **`docker-compose.yml` at the root** instead of `infrastructure/docker/` because Compose reads `.env` from the
  project directory. A root file means one `.env` serves both Compose and the service, and the documented commands work
  without `-f`. Supporting files stay in `infrastructure/docker/`.
- **Later stores (Qdrant, Redis, Chroma) will be Compose profiles**, so `docker compose up -d` keeps starting only PostgreSQL.

## Consequences

- Clear separation between "learn" folders and "build" folders.
- When a second deployable is genuinely needed (for example an MCP server in milestone 5), it becomes a sibling under `services/`.
