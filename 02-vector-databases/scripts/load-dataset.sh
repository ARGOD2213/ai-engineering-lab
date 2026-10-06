#!/usr/bin/env bash
# Loads the 30-document sample dataset into the running service.
# Safe to run repeatedly: documents have fixed ids, so a second run updates instead of duplicating.
# Usage: ./02-vector-databases/scripts/load-dataset.sh [base-url]
set -euo pipefail

BASE_URL="${1:-http://localhost:8080}"
DATASET="$(dirname "$0")/../dataset/documents.json"

curl --fail-with-body -sS -X POST "$BASE_URL/api/documents/batch" \
  -H 'Content-Type: application/json' \
  --data @"$DATASET"
echo
