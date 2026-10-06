#!/usr/bin/env bash
# Verifies that the pgvector extension is installed and usable inside the running container.
# Usage (from the repository root): ./infrastructure/docker/postgres/verify-pgvector.sh
set -euo pipefail

cd "$(dirname "$0")/../../.."

docker compose exec -T postgres sh -c \
  'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f /verify/verify-pgvector.sql'
