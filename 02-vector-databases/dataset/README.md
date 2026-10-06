# Sample dataset

A small, controlled dataset for testing semantic retrieval. It does not depend on any database: the same files are
loaded into pgvector now and into Chroma, Qdrant and Redis later.

| File | Content |
|---|---|
| `documents.json` | 30 short documents. The file is directly the request body of `POST /api/documents/batch`. |
| `queries.json` | 16 questions with the expected document id and the kind of challenge. |

## Categories (4–5 documents each)

HR policies · Java documentation · Spring Boot documentation · customer-support FAQs · product descriptions ·
loan/business policies · technical documentation.

Each document has metadata: `category`, `department`, `documentType`, `source`. Ids are readable and fixed
(`hr-emergency-leave`), so re-loading the file updates the documents instead of duplicating them.

## Designed challenges

| Challenge | Example |
|---|---|
| **Different wording, same meaning** (PARAPHRASE) | "time off when a close relative passes away" → *emergency leave … death in the family* |
| **Same word, different meaning** (KEYWORD_TRAP) | "beans" (Spring beans vs. coffee beans), "thread" (virtual threads vs. thread count), "leave" (time off vs. leave a review), "return" (return type vs. returning a product) |
| **Unrelated documents sharing keywords** | "default" appears in loan default and in Java default methods; "no longer" appears in garbage collection and in a refund question |
| **Ambiguous** | "check that my running service is healthy" → Actuator *or* Docker health checks |

## Ground rules if you extend it

- Keep documents short (under ~90 words). The default model truncates at 128 tokens.
- Add a query to `queries.json` for every new trap, with the expected id.
- Never change an existing id. The evaluation test and the docs reference them.
