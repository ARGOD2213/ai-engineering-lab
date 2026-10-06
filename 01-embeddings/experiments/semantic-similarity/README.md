# Experiment: same meaning vs. different meaning

**Goal:** see, with numbers, that an embedding model places texts with the same *meaning* close
together even when they share few *words*. No LLM and no database are involved. This is just the model.

## Inputs

| Label | Text |
|---|---|
| A | Employees receive 12 casual leave days every year. |
| B | Workers are entitled to twelve days of casual leave annually. |
| C | Spring Boot provides dependency injection. |

A and B say the same thing with different words ("employees/workers", "12/twelve",
"every year/annually"). C is about something unrelated.

## How to run

```bash
# service running (see the root README)
./01-embeddings/experiments/semantic-similarity/run.sh
```

Or call the endpoint directly:

```bash
curl -s -X POST http://localhost:8080/api/embeddings/similarity \
  -H 'Content-Type: application/json' \
  --data @01-embeddings/experiments/semantic-similarity/request.json
```

The service embeds all three texts in **one batched model call** and computes:

- **cosine similarity** of every pair of vectors (1 = same direction, 0 = unrelated, negative = opposite)
- **keyword overlap**, a naive baseline: the share of meaningful words two texts have in common (Jaccard index)

The same check runs as an automated test:
`RealModelSemanticSimilarityTest` (`./mvnw test -Preal-model` in `services/ai-engineering-api`).

## Results (measured, all-MiniLM-L6-v2, 384 dimensions)

| Pair | Cosine similarity | Keyword overlap | Meaning |
|---|---|---|---|
| A ↔ B | **0.889** | 0.25 | same |
| A ↔ C | 0.070 | 0.00 | different |
| B ↔ C | 0.044 | 0.00 | different |

## What happened

1. **A ↔ B scored 0.889** even though only 3 of their 12 distinct meaningful words are shared
   ("casual", "leave", "days"). A keyword system sees 25% overlap. The embedding model sees almost the
   same meaning.
2. **A ↔ C and B ↔ C are close to 0.** HR policy and dependency injection point in unrelated
   directions of the 384-dimensional space.
3. The absolute numbers depend on the model. OpenAI's `text-embedding-3-small` will produce different
   values (its scores are usually more compressed), but the *ordering* should hold. Compare the
   ordering, not the raw score, when you switch models.

## Part 2: the token limit (a limitation you cannot see)

The tokenizer Spring AI ships for this model **truncates input at 128 tokens** (roughly 90–100
English words). Anything after that never reaches the model, and you get no error.

Measured with the same endpoint:

| Comparison | Cosine similarity |
|---|---|
| 128-word HR text vs. the same text + an unrelated sentence about the cafeteria | **1.0000** (the extra sentence was dropped) |
| short HR sentence vs. the same sentence + the cafeteria sentence | 0.67 to 0.77 (the extra sentence changed the vector) |

**Consequence:** long documents must be split into chunks before embedding. Otherwise their endings are
not searchable at all. Chunking belongs to milestone 3 (RAG). `RealModelSemanticSimilarityTest`
also checks this behaviour.

## Questions to explore with ChatGPT

- Why is cosine similarity preferred over Euclidean distance for text embeddings?
- These vectors have an L2 norm of about 6.7, not 1 (Spring AI's ONNX pipeline does not normalise them).
  Why does that not matter for cosine distance but would matter for inner product?
- Would "Employees do NOT receive casual leave" be close to A? (Try it. Negation is a known weakness.)
