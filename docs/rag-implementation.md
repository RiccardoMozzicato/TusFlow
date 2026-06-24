# RAG Implementation (AIProcessingService)

How Retrieval-Augmented Generation works in this service: the modular retrieval pipeline,
conversational memory, token streaming, and Langfuse tracing/scoring. This is the single reference for
"how does the chat answer questions about my documents, and where do I change it?"

## TL;DR

- **Endpoint:** `POST /api/rag/query` (sync) and `POST /api/rag/query/stream` (streamed over STOMP).
- **Retrieval:** Spring AI **modular RAG** (`RetrievalAugmentationAdvisor`) over pgvector, scoped to the
  files the user can access (owned + shared), with optional query rewrite/expansion and a similarity
  threshold.
- **Memory:** per-conversation chat memory in Postgres (`SPRING_AI_CHAT_MEMORY`), keyed
  `"{userId}:{conversationId}"` so conversations are isolated per authenticated user; follow-ups are
  compressed into standalone queries.
- **Streaming:** answers are pushed token-by-token over the existing per-user STOMP/SockJS channel
  (`/user/queue/rag`), not SSE.
- **Observability:** every query is a Langfuse trace (session = conversation), the upload + embedding
  pipeline are first-class traces, and optional relevance/groundedness scores can be attached.
- **Config:** everything lives under `app.rag.*` in `application.yml`.
- **Built on:** Spring AI **1.1.2** (`spring-ai-rag`, `spring-ai-starter-model-chat-memory-repository-jdbc`).

> Embeddings (how documents become vectors) are a separate concern — see
> [embedding-configuration.md](./embedding-configuration.md). This document covers the *query* side
> and the tracing of both sides.

---

## 1. Where each knob lives

All RAG configuration is under `app.rag.*` in `AIProcessingService/src/main/resources/application.yml`.

| What | Property | Env var | Default |
|------|----------|---------|---------|
| Chunks retrieved per query | `app.rag.retrieval.top-k` | `RAG_TOP_K` | `5` |
| Min cosine similarity (drop weaker) | `app.rag.retrieval.similarity-threshold` | `RAG_SIMILARITY_THRESHOLD` | `0.0` (off) |
| Rewrite the query before search | `app.rag.retrieval.query-rewrite` | `RAG_QUERY_REWRITE` | `true` |
| Expand into multiple query variants | `app.rag.retrieval.query-expansion` | `RAG_QUERY_EXPANSION` | `false` |
| Conversation window (messages kept) | `app.rag.memory.max-messages` | `RAG_MEMORY_MAX_MESSAGES` | `20` |
| Serve the streaming endpoint | `app.rag.streaming.enabled` | `RAG_STREAMING_ENABLED` | `true` |
| Master switch for eval scores | `app.rag.scoring.enabled` | `RAG_SCORING_ENABLED` | `false` |
| Post relevance score | `app.rag.scoring.relevance` | `RAG_SCORING_RELEVANCE` | `true` |
| Post groundedness score (LLM judge) | `app.rag.scoring.groundedness` | `RAG_SCORING_GROUNDEDNESS` | `false` |
| Langfuse host for scores REST API | `app.rag.scoring.langfuse-base-url` | `LANGFUSE_BASE_URL` | `https://cloud.langfuse.com` |
| Langfuse auth for scores | `app.rag.scoring.langfuse-auth-header` | `LANGFUSE_AUTH_HEADER` | _(empty)_ |
| Chat-memory schema init (must stay off) | `spring.ai.chat.memory.repository.jdbc.initialize-schema` | — | `never` |

Bound by `RagProperties` (`rag/retrieval/RagProperties.kt`), registered via `@EnableConfigurationProperties`
on `AiProcessingServiceApplication`.

> **`similarity-threshold` is `0.0` (off) on purpose.** A non-zero threshold could filter out every chunk
> before the score distribution of the active embedding model (Qwen3-Embedding-4B) is known. Raise it
> (e.g. `0.5`) once Langfuse traces show typical similarity scores — see §6.

---

## 2. Package layout

```
g11.aiprocessingservice
├── rag/                          # the RAG subsystem (cohesive feature package)
│   ├── RagController.kt          # POST /api/rag/query  +  /query/stream
│   ├── RagService.kt             # orchestrator: queryWithRag() + streamQuery()
│   ├── RagUserContext.kt         # userId (JWT subject) + bearer token from SecurityContext
│   ├── dto/
│   │   ├── RagQueryRequest.kt     # { query, conversationId?, topK?, filters? }
│   │   ├── RagQueryResponse.kt    # { query, answer, sourcesCount, sources, accessibleFilesCount }
│   │   ├── DocumentSource.kt      # one cited chunk (content, preview, metadata, similarity)
│   │   ├── RagStreamEvent.kt      # SOURCES | TOKEN | DONE | ERROR (carries streamId)
│   │   └── RagStreamHandle.kt     # { streamId } returned by the streaming endpoint
│   ├── retrieval/
│   │   ├── RagAdvisorFactory.kt   # builds the RetrievalAugmentationAdvisor + memory advisor
│   │   └── RagProperties.kt       # @ConfigurationProperties("app.rag")
│   ├── memory/
│   │   ├── ChatMemoryConfig.kt    # MessageWindowChatMemory bean (JDBC-backed)
│   │   └── ConversationResolver.kt# memory key + hasHistory
│   ├── streaming/
│   │   └── RagStreamPublisher.kt  # pushes stream frames to /user/queue/rag
│   └── eval/
│       ├── RagScoringService.kt   # async, gated relevance + groundedness scoring
│       └── GroundednessEvaluator.kt # LLM-as-judge (opt-in)
├── tracing/
│   ├── RagTracing.kt             # query trace tagging + the "RAG Query" span
│   ├── PipelineTracing.kt        # tags upload-analysis + embedding spans
│   ├── LangfuseObservationFilter.kt # adds gen_ai.prompt/completion to ChatModel spans
│   └── TraceContextInterceptor.kt   # promotes traceparent from Kafka payloads to headers
└── langfuse/
    └── LangfuseScoreClient.kt    # POST {base}/api/public/scores (REST, not OTLP)
```

---

## 3. The query path (`RagService.queryWithRag`)

```
POST /api/rag/query { query, conversationId, topK }
  │
  ├─ RagUserContext.currentUserId()      → userId from JWT subject (NOT the request body)
  ├─ RagUserContext.currentToken()       → user's bearer token (for downstream calls)
  ├─ ConversationResolver.memoryKey(...) → "{userId}:{conversationId}"
  ├─ RagTracing.tagTrace(...)            → langfuse.user.id, langfuse.session.id = memoryKey
  │
  ├─ FileAccessService.getAccessibleFileIds(userId, token)   → owned + shared file ids
  │     └─ empty → early return ("upload/request sharing first"), no LLM call
  │
  ├─ FilterExpressionBuilder.in("fileId", …)                 → scope retrieval to those files
  ├─ RagAdvisorFactory.retrievalAdvisor(filter, topK, compressHistory)
  ├─ RagAdvisorFactory.memoryAdvisor(memoryKey)
  │
  └─ RagTracing.traceQuery("RAG Query") {
         chatClient.prompt()
           .advisors(retrievalAdvisor, memoryAdvisor, SimpleLoggerAdvisor)
           .user(query).call().chatClientResponse()
         answer  = response.chatResponse().result.output.text
         sources = response.context()[DOCUMENT_CONTEXT]   ← retrieved docs, no 2nd search
         scoringService.scoreAsync(currentTraceId, answer, sources)   ← gated, async
     }
```

**Key properties:**
- **One retrieval.** The advisor runs the vector search once during its `before()` stage; the retrieved
  documents are read back from the response context under
  `RetrievalAugmentationAdvisor.DOCUMENT_CONTEXT`. (The old code searched once manually *and* again
  inside `QuestionAnswerAdvisor`.)
- **Identity from the JWT.** `userId` is the JWT `sub`, never the request body — the client cannot query
  another user's files or memory.
- **Access scoping** is unchanged in spirit from before: `FileAccessService` calls TusUploaderService
  with the user's own token to list owned + shared file ids, and retrieval is filtered to those ids.

### The modular advisor (`RagAdvisorFactory`)

`RetrievalAugmentationAdvisor` is assembled per request from:

| Stage | Component | Notes |
|-------|-----------|-------|
| pre-retrieval | `CompressionQueryTransformer` | Only when the conversation has history — folds a follow-up ("and renewals?") into a standalone query. |
| pre-retrieval | `RewriteQueryTransformer` | Gated by `query-rewrite`. Cleans phrasing for retrieval. |
| pre-retrieval | `MultiQueryExpander` | Gated by `query-expansion`. Generates variants, unions hits (recall ↑, extra LLM call). |
| retrieval | `VectorStoreDocumentRetriever` | `similarityThreshold` + `topK` + the `fileId IN (…)` filter. |
| generation | `ContextualQueryAugmenter(allowEmptyContext=true)` | On empty retrieval, still asks the model — the system prompt (`rag-prompt.st`) says to answer "I don't have enough information" rather than hallucinate. |

The **memory advisor** (`MessageChatMemoryAdvisor`) runs *before* the retrieval advisor (`order` 10 < 20)
so the conversation history is present when the compression transformer needs it.

The query LLM uses the **Groq-backed `openAiChatModel`** directly (via `ChatClient.builder(chatModel)`)
for rewrite/compression/expansion, mirroring `AiConfig`; the answer is generated by `ragChatClient`
(system prompt = `prompts/rag-prompt.st`).

---

## 4. Conversational memory

| Aspect | Detail |
|--------|--------|
| Store | `JdbcChatMemoryRepository` → Postgres table `SPRING_AI_CHAT_MEMORY` |
| Window | `MessageWindowChatMemory`, `maxMessages = app.rag.memory.max-messages` (default 20) |
| Key | `"{userId}:{conversationId}"` built by `ConversationResolver`; `userId` is the JWT subject |
| Isolation | Two users with the **same** `conversationId` get **different** keys → never share a thread |
| Fresh conversation | A missing/malformed client `conversationId` starts a new random one |
| Schema ownership | **Flyway `V11__create_chat_memory.sql`**, not the framework (`initialize-schema: never`) |

### Why a custom migration

The framework's shipped `schema-postgresql.sql` declares `conversation_id VARCHAR(36)` (one UUID). Our
namespaced key is two UUIDs plus a separator (~73 chars), so `V11` widens it to `VARCHAR(100)`. Other
columns/index match the shipped schema:

```sql
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(100) NOT NULL,   -- widened from 36 for "{userId}:{conversationId}"
    content TEXT NOT NULL,
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER','ASSISTANT','SYSTEM','TOOL')),
    "timestamp" TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
ON SPRING_AI_CHAT_MEMORY(conversation_id, "timestamp");
```

### Frontend

The chat widget (`frontend/src/features/chat/`) mints a `conversationId` (UUID) when the chat loads and
regenerates it on **Clear conversation** — so "Clear" starts a fresh memory thread and a fresh Langfuse
session. The id is **ephemeral** (a page reload starts a new conversation; no persistence layer).

---

## 5. Streaming (STOMP, not SSE)

Streaming reuses the existing per-user STOMP/SockJS channel that already powers live job status (the
gateway can't cleanly proxy raw SSE — see the realtime design notes), so there is **no broker
reconfiguration** and the broker stays subscribe-only.

```
POST /api/rag/query/stream { query, conversationId, topK }
  → 202 { streamId }                       (returns immediately)
  → RagService.streamQuery(...) @Async:
        chatClient…stream().chatClientResponse().subscribe(
            onNext  → first frame: SOURCES (from context); then TOKEN per delta
            onError → ERROR
            onComplete → DONE )
        each frame → RagStreamPublisher → convertAndSendToUser(userId, "/queue/rag", frame)

Browser: subscribes to /user/queue/rag (ragSocket.ts), correlates frames by streamId.
```

| Concern | How it's handled |
|---------|------------------|
| Auth on the async thread | `userId` + token are resolved on the **request** thread and passed in; the async method never reads `SecurityContextHolder`. |
| Targeting the right user | `convertAndSendToUser(userId,…)` matches the STOMP session Principal (JWT `sub`), exactly like `JobStatusService.broadcast`. |
| Frame ordering | One `SOURCES` (guarded by an `AtomicBoolean`), then `TOKEN`s, then `DONE`/`ERROR`. |
| No accessible files | Emits the guidance text as a `TOKEN` then `DONE` (no LLM call). |
| Disable | `app.rag.streaming.enabled=false` → endpoint returns `503`. The non-streaming `askRag` path remains as a fallback. |

Frontend: `ragSocket.ts` mirrors `jobsSocket.ts` (shared SockJS connection, subscribes `/user/queue/rag`);
`chatSlice` accumulates `SOURCES/TOKEN/DONE/ERROR` into one assistant turn (tolerant of frames that
arrive before the POST resolves); `ChatPanel` renders incrementally and shows "Thinking…" only until the
first frame.

---

## 6. Observability (Langfuse)

Traces are exported via **OTLP** (`management.otlp.tracing.*`); the OpenTelemetry trace id equals the
Langfuse trace id.

### RAG query trace (`RagTracing`)
- Trace root tagged with `langfuse.user.id` and `langfuse.session.id = "{userId}:{conversationId}"` — so
  all turns of one conversation group into a single Langfuse session.
- A `RAG Query` span records the answer plus retrieval stats: `rag.sourcesCount`, `rag.similarity.min`,
  `rag.similarity.max` (handy for choosing a `similarity-threshold`).
- The child ChatModel generation span carries `gen_ai.prompt` / `gen_ai.completion`, added by
  `LangfuseObservationFilter`.

### Pipeline traces (`PipelineTracing`)
The Kafka ingestion stages run under an ambient span (a manual span in `MetadataProcessor`; Spring Kafka
observation on the embedding listeners, linked via the propagated `traceparent`). `PipelineTracing` tags
that span best-effort:
- **Upload analysis** — user, file id/content type, selected model + client (pdf/image/generic), chars sent.
- **Embedding prep** — chunk + batch counts.
- **Embedding generation** — per-batch model, dimensions, fileVersionId, chunk count.

### Evaluation scores (`RagScoringService` → `LangfuseScoreClient`)
Disabled by default (`app.rag.scoring.enabled=false`); async + best-effort so they never affect the
response. Scores POST to `{langfuse-base-url}/api/public/scores` with the OTLP Basic auth header and link
to the query trace by its OTel trace id:
- **`relevance`** — mean similarity of retrieved sources (cheap, deterministic).
- **`groundedness`** — `GroundednessEvaluator` LLM-as-judge in `[0,1]` (opt-in; one extra LLM call).

To enable: set `RAG_SCORING_ENABLED=true` and `LANGFUSE_AUTH_HEADER` (+ `LANGFUSE_BASE_URL` for non-EU).

---

## 7. API contract

### `POST /api/rag/query`
Request:
```json
{ "query": "What does the contract say about termination?",
  "conversationId": "f1e2…",   // optional; a fresh one is used if omitted
  "topK": 5 }                    // optional; falls back to app.rag.retrieval.top-k
```
Response:
```json
{ "query": "…", "answer": "…", "sourcesCount": 3,
  "sources": [ { "content": "…", "contentPreview": "…",
                 "metadata": { "fileId": "…", "filename": "…", "distance": 0.21 },
                 "similarity": 0.79 } ],
  "accessibleFilesCount": 12 }
```

### `POST /api/rag/query/stream`
Same request body. Returns `202 { "streamId": "…" }`; the answer then arrives as `RagStreamEvent`s on the
`/user/queue/rag` STOMP destination, correlated by `streamId`.

Both require role `admin`, `manager`, or `staff`. `similarity = 1 - cosine_distance`, clamped to `[0,1]`.

> **Back-compat:** the request still accepts a `filters` map but it is **ignored** for identity — the
> user is taken from the JWT. Older clients that sent `filters.userId` keep working.

---

## 8. Dependencies

```kotlin
// build.gradle.kts
implementation("org.springframework.ai:spring-ai-rag")                                   // modular RAG advisor
implementation("org.springframework.ai:spring-ai-starter-model-chat-memory-repository-jdbc") // chat memory
// (kept) spring-ai-advisors-vector-store, spring-ai-starter-vector-store-pgvector, …
```
Versions are managed by the `spring-ai-bom` 1.1.2. All Spring AI APIs used here were verified against the
actual 1.1.2 jars (real packages: `…rag.preretrieval.query.transformation`, `…rag.generation.augmentation`,
`…rag.retrieval.search`, `…rag.advisor`).

---

## 9. Testing

| Test | Covers |
|------|--------|
| `RagServiceTest` | topK propagation, distance→similarity, compression only with history, empty-context, no-access guard, streaming frame order |
| `ConversationResolverTest` | per-user namespacing, cross-user isolation, fresh-id fallback, `hasHistory` |
| `RagScoringServiceTest` | gating (disabled / no traceId), relevance = mean similarity, groundedness |
| `chatSlice.test.ts` (frontend) | streaming token accumulation, out-of-order frames, error, conversationId reset |
| `AiProcessingServiceApplicationTests` + integration | full Spring context boots with all RAG beans; `V11` applies (testcontainers Postgres + Kafka) |

Run: `./gradlew test` (backend; testcontainers needs Docker) · `npm run test && npm run typecheck`
(frontend — `npm run lint` is not configured).

---

## 10. Operational notes & tuning

- **Tune `similarity-threshold` from data.** Run real queries with scoring on, read `rag.similarity.*`
  on the traces, then set the threshold just below the useful band (≈0.4–0.6 is typical for cosine).
- **`query-expansion` costs latency.** It adds an LLM call + N searches; enable only if recall is a
  measured problem.
- **Memory window vs. cost.** Larger `max-messages` = more context tokens per turn. 20 is a balance;
  lower it for cheaper/faster turns.
- **Streaming + slow models.** The biggest perceived-latency win is with self-hosted reasoning models
  that think for tens of seconds; with fast Groq models the sync endpoint is already snappy.
- **Scores are async and best-effort.** A Langfuse outage or a bad auth header degrades to a logged
  warning — it never fails or slows a query.

---

## 11. Delivery history

Shipped as six stacked PRs against `feat/ui-improvement` (each independently reviewable/testable):

| PR | Phase |
|----|-------|
| #14 | Package carve-out (`rag/`, `tracing/`) + query bug fixes (double retrieval, test tags, similarity) |
| #15 | Modular `RetrievalAugmentationAdvisor` pipeline |
| #16 | Conversational memory (JDBC, per-user isolation, `V11`) |
| #17 | Langfuse RAG trace polish (real session, retrieval stats) |
| #18 | Streaming over STOMP |
| #19 | Upload + embedding pipeline tracing |
| #20 | Langfuse evaluation scores |

**Deferred:** Langfuse prompt management (loading `rag-prompt.st` from Langfuse with a file fallback) —
was the optional slice of Phase 6.
