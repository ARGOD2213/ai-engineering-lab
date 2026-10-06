#!/usr/bin/env bash
# Experiment: same meaning vs. different meaning, using only an embedding model (no LLM, no database).
# Usage (service must be running): ./01-embeddings/experiments/semantic-similarity/run.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
DIR="$(dirname "$0")"

RESPONSE=$(curl --fail-with-body -sS -X POST "$BASE_URL/api/embeddings/similarity" \
  -H 'Content-Type: application/json' --data @"$DIR/request.json")

if command -v jq >/dev/null 2>&1; then
  echo "$RESPONSE" | jq '{model, dimensions, texts, pairs}'
elif command -v python3 >/dev/null 2>&1; then
  echo "$RESPONSE" | python3 -m json.tool
else
  echo "$RESPONSE"
fi
