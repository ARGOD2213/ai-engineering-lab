# Interview prep: embeddings

Answer in your own words first, then check. Each answer links to something you built.

## Fundamentals

**1. What is an embedding?**
A fixed-length vector of floats produced by a trained model, where geometric closeness reflects semantic similarity.
In this lab: 384 floats from all-MiniLM-L6-v2 (`POST /api/embeddings`).

**2. How do you measure similarity between two embeddings? Why cosine?**
Cosine similarity: dot product divided by the product of the lengths. It compares direction and ignores magnitude.
Our vectors have length ≈ 6.7 (not normalised), so inner product would be biased; cosine is not. pgvector: `<=>` is cosine *distance* = 1 − similarity.

**3. Two sentences share almost no words but score 0.89. How?**
The model learned that "employees/workers", "12/twelve" and "every year/annually" are used in the same contexts.
Similarity comes from meaning, not spelling. (Experiment: A ↔ B = 0.889 with 25% keyword overlap.)

**4. What determines the vector dimension? Can you change it?**
The model architecture. MiniLM = 384, text-embedding-3-small = 1536 (it can be shortened through an API parameter).
Storage and index must match exactly; we verify the actual size at startup.

**5. Is a bigger dimension always better?**
No. Quality depends on the model and its training data. More dimensions cost storage, memory and distance-computation time.

## Engineering

**6. You switch from MiniLM to OpenAI embeddings. What breaks and what do you do?**
Old vectors are in a different space and have a different size. Queries embedded with the new model cannot be compared
with them. Create a new index/table, re-embed every document (and pay for it), validate retrieval quality, switch reads, drop the old table.
Our design has one table per model and a startup dimension check.

**7. A document is 3 pages long. What happens if you embed it with MiniLM here?**
Only the first 128 tokens are used and the rest is silently dropped. We proved it: long text vs. long text + tail = cosine 1.0000.
The fix is chunking (with overlap) before embedding.

**8. Local model or hosted API? Trade-offs?**
Local: free per call, private, low latency, but you operate it (CPU, RAM, native libraries) and quality may be lower.
Hosted: better quality, multilingual, no ops, but cost per token, network latency, rate limits, and data leaves your boundary.

**9. How would you reduce embedding cost at scale?**
Embed once and store (never re-embed on read); deduplicate identical texts (hash → cache); batch requests; use the
provider's async batch API; choose a smaller or shortened-dimension model where the quality is sufficient; avoid re-embedding on metadata-only updates.

**10. How do you test code that depends on an embedding model without paying for API calls?**
Mock `EmbeddingModel` in unit tests; use a deterministic fake (feature hashing) for integration tests; keep a small,
opt-in suite against the real model with assertions on *ordering* rather than exact scores. That is this repository's setup.

**11. What would you log around embedding calls?**
Model, input size, vector size, latency, errors by type. Never the text (personal data) and never provider error bodies
(they can contain request details). Metrics: Spring AI's `gen_ai.client.operation`.

**12. Name known weaknesses of embedding similarity.**
Negation ("does NOT receive leave"), exact numbers and identifiers ("12 days" vs "15 days", order IDs), domain jargon
the model never saw, and closely related concepts (our loan default vs. prepayment miss). Hybrid search and re-ranking address some of these.

## Stretch (ask ChatGPT)

- How are embedding models trained (contrastive learning, positive/negative pairs)? Conceptual level only.
- What is Matryoshka representation learning and why can text-embedding-3 vectors be shortened?
- What is the difference between a bi-encoder (embeddings) and a cross-encoder (re-ranker)?
