# Vector search flow

`POST /api/search`, pure vector retrieval with no LLM.

```mermaid
flowchart LR
    Q["User query<br/>'How much time off when a<br/>relative passes away?'"] --> E["EmbeddingModel<br/>(same model as the documents)"]
    E --> V["Query vector<br/>float[384]"]
    V --> S["pgvector similarity search<br/>ORDER BY embedding #lt;=#gt; query<br/>LIMIT topK"]
    S --> R["Top-K documents<br/>text · metadata · score"]
```

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant API as SearchController
    participant S as SemanticSearchService
    participant VS as VectorStore (PgVectorStore)
    participant M as EmbeddingModel
    participant DB as PostgreSQL + pgvector

    C->>API: {"query": "...", "topK": 5, "similarityThreshold": 0.3, "filterExpression": "department == 'HR'"}
    API->>API: validate (1 ≤ topK ≤ 20, 0 ≤ threshold ≤ 1)
    API->>S: search(...)
    S->>S: build SearchRequest (parse filter → 400 if invalid)
    S->>VS: similaritySearch(request)
    VS->>M: embed(query)
    M-->>VS: float[384]
    VS->>DB: SELECT *, embedding #lt;=#gt; :q AS distance<br/>WHERE distance #lt; 1 - threshold AND metadata @@ jsonpath<br/>ORDER BY distance LIMIT topK
    DB-->>VS: rows
    VS-->>S: List#lt;Document#gt; (score = 1 - distance)
    S-->>API: SearchResult (rank, id, text, metadata, score, distance)
    API-->>C: 200 {"resultCount", "results": [...]}
```

## Parameters and what they really do

| Parameter | Effect | Watch out |
|---|---|---|
| `topK` (default 5, max 20) | `LIMIT` on the nearest neighbours | always returns K rows if available, relevant or not |
| `similarityThreshold` (default 0) | `WHERE distance < 1 - threshold` | 0 still drops documents with negative similarity |
| `filterExpression` | Spring AI portable filter → `metadata @@ '$.department == "HR"'` | filter + approximate index can yield fewer than K results |

## Score vs. distance

- `distance` = cosine distance from pgvector's `<=>`: 0 = same direction, 1 = unrelated, 2 = opposite.
- `score` = `1 - distance` = cosine similarity. Higher is better.
- Scores are **model-specific** and **not probabilities**. In our evaluation a correct match scored 0.35 and a wrong one 0.57.

## Why no LLM here

The output is a ranked list of existing texts. Nothing is generated, so nothing can be hallucinated. That makes retrieval
quality measurable on its own, which RAG (milestone 3) depends on.
