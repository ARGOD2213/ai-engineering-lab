# Docker infrastructure

`docker-compose.yml` lives at the **repository root** (so it reads the root `.env`). This folder holds the files it mounts.

```text
infrastructure/docker/postgres/
├── init/01-enable-pgvector.sql        # runs once on first volume initialisation
├── verify/verify-pgvector.sql         # smoke test: type, operators, HNSW index (rolled back)
└── verify-pgvector.sh                 # runs the smoke test inside the container
```

## Services

| Service | Image | Port (host) | Started by default |
|---|---|---|---|
| `postgres` | `pgvector/pgvector:0.8.7-pg17` | `127.0.0.1:${POSTGRES_PORT:-5433}` | yes |
| qdrant / redis / chroma | n/a | n/a | milestone 2, as Compose profiles |

## Commands (run from the repository root)

```bash
cp .env.example .env            # once; choose a POSTGRES_PASSWORD
docker compose up -d            # start in the background
docker compose ps               # status; wait for "(healthy)"
docker compose logs -f postgres # follow logs (Ctrl+C to stop following)
./infrastructure/docker/postgres/verify-pgvector.sh   # prove pgvector works
docker compose down             # stop and remove the container; data stays in the volume
docker compose down -v          # stop AND delete the data volume (fresh start)
```

Open a SQL shell:

```bash
docker compose exec postgres sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"'
```

Useful queries:

```sql
\dx                                                    -- installed extensions (vector 0.8.7)
\d documents_minilm_l6_v2                              -- table + indexes
SELECT id, vector_dims(embedding), created_at FROM documents_minilm_l6_v2 LIMIT 5;
SELECT id, 1 - (embedding <=> (SELECT embedding FROM documents_minilm_l6_v2 WHERE id = 'hr-casual-leave')) AS similarity
FROM documents_minilm_l6_v2 ORDER BY similarity DESC LIMIT 5;  -- "more like this", in pure SQL
```

## Design notes

- **Persistent named volume** `ai-lab-postgres-data`: data survives `down`/`up`.
- **Health check** with `pg_isready`, so `docker compose ps` shows when the database accepts connections.
- **Configurable** database, user, password and port via `.env`. The password has no default and Compose fails fast.
- **Bound to 127.0.0.1**: not reachable from other machines on your network.
- **Pinned image version**: reproducible across machines; upgrade deliberately.
- The PostgreSQL 17 image is used rather than 18 because the 18 images changed the data directory layout
  (`/var/lib/postgresql/18/docker`). Moving to 18 is a deliberate later step.
