#!/usr/bin/env bash
# Runs one semantic search against the running service.
# Usage: ./02-vector-databases/scripts/search.sh "How much time off do I get when a relative dies?" [topK] [filter]
# Example with a metadata filter:
#   ./02-vector-databases/scripts/search.sh "leave policy" 3 "department == 'HR'"
set -euo pipefail

QUERY="${1:?Usage: search.sh \"question\" [topK] [filterExpression]}"
TOP_K="${2:-5}"
FILTER="${3:-}"
BASE_URL="${BASE_URL:-http://localhost:8080}"

# Minimal JSON escaping for quotes and backslashes in the arguments.
escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

if [ -n "$FILTER" ]; then
  BODY="{\"query\": \"$(escape "$QUERY")\", \"topK\": $TOP_K, \"filterExpression\": \"$(escape "$FILTER")\"}"
else
  BODY="{\"query\": \"$(escape "$QUERY")\", \"topK\": $TOP_K}"
fi

RESPONSE=$(curl --fail-with-body -sS -X POST "$BASE_URL/api/search" -H 'Content-Type: application/json' -d "$BODY")

# Pretty-print when jq or python is available, raw JSON otherwise.
if command -v jq >/dev/null 2>&1; then
  echo "$RESPONSE" | jq '.results[] | {rank, id, score, text}'
elif command -v python3 >/dev/null 2>&1; then
  echo "$RESPONSE" | python3 -m json.tool
else
  echo "$RESPONSE"
fi
