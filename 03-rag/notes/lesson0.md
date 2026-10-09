# Lesson 0: Orientation

**Status: completed — 2026-10-09**

## Goal

Trace how a request reaches the existing embedding model, understand what Spring AI hides, and separate embedding from database storage.

## What I did

- Reviewed the RAG learning service and controller.
- Added an `embed(String)` method to my learning `RagService`, injecting Spring AI's `EmbeddingModel`.
- Exposed the method through `POST /api/rag/embed` and tested it with curl. It returned a 384-number vector for the configured MiniLM model.
- Started the API and read its logs: the Transformers/ONNX model initialized, Spring verified 384 dimensions, HikariCP connected to PostgreSQL, and pgvector schema validation passed.
- Had compilation errors reported in the existing `EmbeddingService`; diagnosed them as Java compilation errors rather than a missing model bean. I fixed them myself; no existing implementation was edited by the tutor.
- Drew the request and future-storage flows from memory, then completed the teach-back and scenario questions.

## Flow I learned

Embedding-only request:

`HTTP request -> DispatcherServlet -> RagController -> RagService -> injected EmbeddingModel -> tokenizer/model inference/pooling -> float[384] response`

Spring injects a provider implementation of `EmbeddingModel` when it builds the application context. The service does not look up the bean on every request. The embedding interface hides provider-specific inference; for the local provider, the pipeline includes tokenization, ONNX inference, and pooling.

Future storage flow:

`document text + metadata -> embedding model -> vector + document fields -> VectorStore/JDBC -> HikariCP connection -> PostgreSQL + pgvector row`

HikariCP is the JDBC connection pool; it does not create embeddings or perform similarity ranking. PostgreSQL stores the document and its vector, while pgvector provides vector operators and indexing for nearest-neighbor search. At query time, embed the query with the same model, search for nearby vectors, and use each matched row's text/metadata as retrieved context.

The embedding-only route does not query PostgreSQL once the application is running. This application does validate the configured vector-store/database during startup, so a database outage before startup can still prevent the app from becoming available.

## Interview terms to remember

- **Bean:** an object managed by Spring's application context; constructor injection gives a class its dependencies.
- **Auto-configuration:** Spring Boot configures beans from the dependencies on the classpath and application properties. The Transformers starter supplies the local `EmbeddingModel` implementation in this setup.
- **Interface / polymorphism:** `EmbeddingModel` is the contract. ONNX and OpenAI can provide different concrete implementations; code typed to the interface can call the same contract while Spring selects the implementation.
- **Embedding:** a fixed-length array of numbers representing text in a model-specific vector space. MiniLM here returns 384 dimensions.
- **Tokenizer:** converts text to token IDs for model input. Token limits are not character limits.
- **Vector space:** a model's learned coordinate system. Two models can both return 384 values and still produce incomparable vectors; use one model consistently for document and query vectors, or keep separate stores and re-embed.
- **pgvector:** a PostgreSQL extension that stores vectors and supports distance operations/indexes; it does not understand text meaning by itself.
- **HikariCP:** manages and reuses JDBC connections to the database; it is separate from model inference and vector ranking.
- **RAG:** retrieves relevant source text at request time and supplies it as context to a chat model. Retrieval is not model training.

## Verify and remaining exercise

- The `/api/rag/embed` request returned 384 floats after the API started.
- The model, dimension check, Hikari connection, and vector-store schema were visible in startup logs.
- We reasoned through the database-down case but did **not** stop the database container to test it. Also, the API's startup-time database validation means this runtime-independence claim applies only after a successful startup.

## Teach-back result

I explained that embedding inference is independent of a database write, that persistence stores a vector with its document references, and that matching dimensions do not make vectors from different models comparable. I also distinguished Spring's interface from its provider implementation.
