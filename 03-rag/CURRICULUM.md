# 03-rag curriculum: build RAG by hand, then move to Spring AI

Rules of the game (the tutor rules live in the repo-root CLAUDE.md): I type every line, I predict before I run, I break things on purpose,
I explain back in my own words, I write 5 lines of notes per lesson, one commit per lesson.

Do not open the PDF's code pages before attempting a lesson. After you finish, compare with the PDF and write down every difference and why.

## Analogies bank (use, then try new ones if one doesn't land)
| AI word | Java / Spring picture |
|---|---|
| chunk | one page of a paginated result |
| token | a byte of the model's buffer: limits are counted in tokens, not characters |
| vocabulary | `HashMap<String,Integer>`, 30,522 entries (line N of vocab.txt = id N) |
| embedding | a `hashCode()` that preserves meaning: `float[384]`, nearby arrays = similar meaning |
| cosine similarity | `compareTo()` for meaning (angle between two arrays); distance = 1 - similarity |
| vector index (HNSW) | a B-tree for "nearest", approximate but fast |
| context window | a fixed-size buffer in front of a stateless function; overflow is cut or rejected |
| top-k | `LIMIT k` | 
| score threshold | `WHERE similarity > x` |
| retrieval / generation | repository layer / service layer |
| prompt | a `String` template with holes |
| eval set | an integration-test suite for answer quality |
| temperature | a randomness knob: 0 = always take the most likely word |
| RAG vs fine-tuning | RAG = a JOIN at query time; fine-tuning = rebuilding the model |

## Lesson 0: Orientation (no new code, 45 min)
- Do: pull latest, branch `rag-learning`, start docker compose (pgvector), run the API, call the embeddings endpoint, run search.sh,
  read the code path, draw the flow (controller -> embedding bean -> store) in notes.
- What / why: what Spring AI hides (tokenizer, ONNX model, pooling), why the first call is slow (model load), RAM use on an 8 GB laptop.
- Strategy question: why Postgres + pgvector instead of a dedicated vector DB? (read 02-vector-databases comparison first)
- Verify: the endpoint returns 384 floats; I can explain each box of my drawing.
- Break it: stop the DB container, call the endpoint, read the error, name the layer.

## Lesson 1: Discover why we chunk (45 min)
- Build: `cosine(float[], float[])` with unit tests (same = 1.0, orthogonal = 0, opposite = -1). Then an experiment: embed the first
  200, 500, 1000, 2000, 5000, 10000 and 50000 characters of the story with the EXISTING bean and print cosine vs the 1000-char prefix.
- PREDICT first: at what length does the similarity stop changing, and why?
- Concepts: token (~4-5 chars of English), fixed input window with SILENT truncation, attention cost grows with n squared, one vector per text.
- Verify: find the plateau, convert chars to tokens (reference: 4.9 chars/token), compare with 256 (the model-card default). If your bean cuts at
  another number you found the effective limit: write it down. (Not run on your repo by me: treat what you see as the truth.)
- Break it: compare the question "How much was the Series A?" against (a) the whole-document vector and (b) a Chapter 10 vector. Which wins? Why?

## Lesson 2: Sentence-aware chunker, test first (60 min)
- Build tests first: no chunk > 1000 chars; no blank chunk; every sentence is whole inside at least one chunk; consecutive chunks overlap by
  >= 150 chars; "Dr. Rao paid Rs. 18 lakh. Then she left." is 2 sentences; a 3000-char sentence is hard-split at a space; empty input.
  Then implement `SentenceChunker(maxChars, overlapChars)` with plain String/List methods only.
- Concepts: chunk size comes from the model's token limit (1000 chars is about 190-225 tokens, headroom under 256), why overlap,
  why sentence boundaries, the abbreviation trap.
- Strategy question: fixed-size vs sentence vs paragraph vs recursive vs semantic chunking. Trade-offs? Why do we pick sentence + overlap here?
- Verify: story gives roughly 60-80 chunks (reference 71); all invariants green.
- Break it: write a throwaway naive splitter (substring every 1000) and count sentences cut in half (reference 46 of 387 without overlap).
  Look at 3 of them with your own eyes.

## Lesson 3: Tokens for real (60-90 min)
- 3A (required): count tokens per chunk with whatever tokenizer exists in the stack (ask the tutor to help you FIND it, not write it);
  assert every chunk <= 256; print the 5 most token-dense chunks.
- 3B (deep dive, recommended): implement WordPiece yourself. Get vocab.txt from the model's Hugging Face repo (line number = id).
  lowercase -> split punctuation -> greedy longest match, continuation pieces start with "##" -> add [CLS] (101) and [SEP] (102). Compare with 3A on all chunks.
- Concepts: subword tokens ("HikariCP" -> hi ##kari ##cp), why unknown words still work, padding + attention mask (tensors are rectangular),
  truncation, mean pooling over real tokens only, why the output is 384 numbers (hidden size).
- Verify: all chunks <= 256; 3B matches 3A.
- Break it: build a 5000-char chunk, see how many tokens fall past 256 and which words get cut mid-word. Do the toy pooling with and without the mask.

## Lesson 4: EmbeddingClient abstraction and batching (45 min)
- Build: interface `EmbeddingClient { List<float[]> embedAll(List<String>); default float[] embed(String) }`, an adapter over the existing bean,
  a `partition(list, size)` helper. Tests: every vector has 384 floats; order is preserved (embed [a,b] vs [b,a]); same text gives the same vector;
  is the vector unit length? Find out, don't assume.
- Concepts: Strategy pattern (later swap ONNX / HF API / Python FastAPI), why batch (fixed overhead per call), the SAME model must embed documents
  and questions, embedding model vs chat model, cosine doesn't need normalised vectors but dot product does.
- Strategy question: in-process ONNX vs HTTP API vs Python sidecar: privacy, latency, ops burden, RAM on 8 GB.
- Verify: tests green. Optional: compare the first 5 floats with Python sentence-transformers (all-MiniLM-L6-v2).
- Break it: time 50 texts in one call vs 16 per call vs 1 per call.

## Lesson 5: pgvector storage (60-90 min)
- Build: table `shopnest_chunks` (id, doc_id, chunk_index, content, token_count, embedding vector(384), unique(doc_id, chunk_index)),
  following however the repo already creates schema. Repository with JdbcTemplate first (JPA + Hibernate vector type later, optional).
  `IngestionService`: chunk -> embed -> store; embed OUTSIDE the DB transaction; re-ingesting the same doc must not duplicate rows.
- psql labs: `<=>` cosine distance, similarity = 1 - distance, compare `<=>` / `<->` / `<#>`, self-retrieval (a chunk's own vector is its own top-1),
  EXPLAIN with and without an HNSW index.
- Concepts: `vector(384)` is a contract, cosine vs L2 vs inner product, exact scan vs approximate index (irrelevant at 71 rows), index operator
  class must match the query operator, why not to hold a DB connection while waiting on a slow model call (your HikariCP knowledge).
- Verify: 71 rows; chunk 29's own vector returns chunk 29 first with score about 1.0.
- Break it: insert a 3-float vector (read the error); ingest twice; query with the wrong operator.

## Lesson 6: Retrieval endpoint, no LLM yet (60 min)
- Build: `POST /api/rag/search {question, k}` -> list of {chunkIndex, score, preview}. Run the 10 answer-key questions, record hit@3.
- PREDICT hit@3 before running.
- Concepts: top-k, what a score means, thresholds, retrieval is not answering, neighbours from overlap show up together.
- Verify: baseline hit@3 written in 03-rag/README.md. For every MISS decide: chunking, embedding, or the question itself?
- Break it: ask the CFO question and 3 other irrelevant questions; look at the best scores; choose MIN_SCORE from data, not from a guess.
  Then deliberately mis-align vectors and chunks (bug) and watch retrieval return plausible nonsense.
- Strategy question: why top-3 and not 1 or 10? Where would keyword search beat vectors (exact IDs)?

## Lesson 7: Generation (90 min)
- Build: `LlmClient` interface, one implementation (Gemini or OpenAI) with Spring `RestClient` (connect + read timeouts), `PromptBuilder`
  (system rule + 3 chunks + question), `RagService.ask`, `POST /api/rag/ask` returning answer + sources. API key from an environment variable.
- Concepts: stateless LLM, context window, system vs user prompt, temperature, hallucination, grounding, token cost math
  (3 chunks is about 600 tokens in + the answer out), why we log chunk ids and scores, prompt injection.
- Check the provider's current docs for request shape and model names; don't trust memory.
- Verify: the "37 customers" question is answered with sources; the CFO question is refused.
- Break it: remove "answer ONLY from the context" and ask the CFO question; run one question 5 times at temperature 0.1 vs 1.0;
  plant "ignore previous instructions and ..." inside a COPY of the story, ingest it, and see what happens (prompt injection).
- Strategy question: where does the refusal live: score threshold, prompt rule, or both? What does each cost and catch?

## Lesson 8: Evaluate and tune (60-90 min)
- Build: an eval test reading questions from a file; add 10 questions of your own; a results table in 03-rag/README.md.
- Experiments, one change at a time: chunk 500 / 1000 / 3000, overlap 0 / 150, chapter-title prefix on every chunk, top-k 1 / 3 / 5, MIN_SCORE sweep.
- Concepts: measure before tuning, retrieval metric (hit@k) vs generation quality, why 10 questions is noisy.
- Verify: at least 6 rows in the table and a written conclusion in your own words.

## Lesson 9: Spring hardening, the Spring-world part (2 sessions)
- Build: `@ConfigurationProperties` for rag.* (chunk size, overlap, topK, minScore); Bean Validation on the request; `@RestControllerAdvice`
  mapping LLM timeout -> 504, provider 5xx -> 502, bad input -> 400 (ProblemDetail); bounded retry with backoff; Spring Cache + Caffeine on
  `embed(question)` (key = normalised question, TTL, size cap); Micrometer timers for embed / search / llm; log chunk ids and scores.
- Concepts: LLM calls are slow, costly, rate-limited, flaky and non-deterministic, so every Spring resilience habit matters MORE here.
  Cache embeddings (deterministic, expensive). Be careful caching LLM answers (stale, context-dependent).
- Check starters and annotations against the Spring Boot version in pom.xml.
- Verify: second identical question skips the embedding (log or timer); an LLM timeout returns a JSON 504; metrics visible.
- Break it: disable the cache and time 20 identical questions; cut the network and read the mapped error.

## Lesson 10: Port to Spring AI abstractions and decide (2 sessions)
- Build: the same pipeline with `VectorStore` (PgVectorStore), `ChatClient`, a retrieval advisor, `TokenTextSplitter`. Compare top-3 and answers with
  your hand-built version on the same 10 questions. Check names against the Spring AI 2.x docs.
- Concepts: what the framework hides; token-splitter default sizes vs MiniLM's 256-token limit (and which tokenizer it counts with);
  when to use the framework vs hand-rolled.
- Verify: a comparison table and a short ADR in docs/ (what I keep, what I replace, why).

## Stretch (after lesson 10)
Hybrid search (Postgres full-text + vectors with reciprocal rank fusion), reranking, streaming answers (SSE), chat history,
tool calling and agents (04-ai-agents), MCP (05-mcp).
