# File Management System with AI-Powered Metadata Enrichment & RAG

**Gruppo 11** — Laboratorio 5
**Web Applications II — Politecnico di Torino**

A microservices-based document portal where files are uploaded with a resumable protocol, enriched
automatically by an LLM (summary, tags, sensitivity — including **vision** analysis of images),
indexed into a vector store, and explored through a **conversational RAG chat** that streams answers
token-by-token. The whole pipeline is event-driven (Kafka + Debezium CDC + Transactional Outbox) and
fully instrumented (Grafana LGTM for system telemetry, Langfuse for LLM traces).

---

## 📋 Table of Contents

1. [Architecture Overview](#-architecture-overview)
2. [Event-Driven Pipeline](#-event-driven-pipeline)
3. [Services Overview](#-services-overview)
4. [AI Metadata Generation](#-ai-metadata-generation)
5. [RAG (Retrieval-Augmented Generation) System](#-rag-retrieval-augmented-generation-system)
6. [Realtime Channel (STOMP/SockJS)](#-realtime-channel-stompsockjs)
7. [Transactional Outbox Pattern](#-transactional-outbox-pattern)
8. [Observability (LGTM + Langfuse)](#-observability-lgtm--langfuse)
9. [Technology Stack](#-technology-stack)
10. [Testing Strategy](#-testing-strategy)
11. [Configuration & Setup](#-configuration--setup)
12. [Features Implemented](#-features-implemented)
13. [Known Limitations](#-known-limitations)
14. [Additional Documentation](#-additional-documentation)

---

## 🏗️ Architecture Overview

The platform is composed of **four Spring Boot / Kotlin microservices**, a **React SPA**, an
**event backbone** (Kafka + Debezium), and an **observability stack** (LGTM + Langfuse). Each
backend service owns its own database; services never call each other's DB. Cross-service workflows
are asynchronous, driven by the **Transactional Outbox Pattern** and **Change Data Capture (CDC)**.

> 📊 **Flow diagrams:** see [`docs/diagrams/architecture-flow.md`](docs/diagrams/architecture-flow.md)
> for the end-to-end sequence (login → document availability) and the Debezium/Kafka CDC topology
> (both render natively on GitHub).

### System Components

```
┌────────────────────────────────────────────────────────────────────────────────────┐
│                                SYSTEM ARCHITECTURE                                  │
└────────────────────────────────────────────────────────────────────────────────────┘

      ┌──────────────────┐                                   ┌──────────────────┐
      │   Frontend SPA   │   served at  /ui  (BFF session)   │    Keycloak      │
      │ React 19 + Vite  │◀─────────────┐      ┌────────────▶│  OAuth2 / OIDC   │
      │   :5173 (dev)    │              │      │             │     :9090        │
      └──────────────────┘              ▼      │             └──────────────────┘
                                 ┌──────────────────┐
                                 │   API Gateway    │  confidential OIDC client
                                 │ Spring Cloud GW  │  session cookie + CSRF
                                 │   (MVC)  :8080   │  TokenRelay (Bearer)
                                 └──────────────────┘
                                          │  Bearer token on every hop
              ┌───────────────────────────┼────────────────────────────┐
              ▼                            ▼                            ▼
    ┌──────────────────┐        ┌──────────────────┐        ┌──────────────────────┐
    │  TusUploader     │        │  UserDetail      │        │  AIProcessing        │
    │  Service :8081   │        │  Service :8082   │        │  Service :8083        │
    │  uploads,        │        │  users, orgs,    │        │  metadata + vision,   │
    │  versions,       │        │  quotas, avatars │        │  embeddings, RAG chat │
    │  sharing, quota  │        │                  │        │  jobs, STOMP realtime │
    └───────┬──────────┘        └────────┬─────────┘        └──────┬─────────┬──────┘
            │                            │                         │         │
            │      ┌─────────────────────┴─────────────────────────┘         │
            │      │   Apache Kafka 4.1 + Kafka Connect / Debezium CDC        │
            │      │   topics: file-outbox · job-outbox · embeddings-batch    │ STOMP push
            │      └─────┬───────────────────────┬───────────────────┬────────┘ (/user/queue)
            │            │                       │                   │
    ┌───────▼────────┐  │  ┌─────────────────┐  │  ┌────────────────▼─────────────┐
    │ PostgreSQL 15  │  │  │ PostgreSQL 15   │  │  │ PostgreSQL 15 + pgvector      │
    │ filedb         │◀─┘  │ userdb          │  └─▶│ aireqdb (jobs, outbox,        │
    │ CDC :5432      │     │ :5433           │     │ vector_store)  CDC :5435      │
    └───────┬────────┘     └─────────────────┘     └───────────────────────────────┘
            │ S3 API                                        ▲ embeddings via
            ▼                                               │ Ollama / OpenWebUI
    ┌───────────────────────────┐                   ┌───────┴───────────────┐
    │  MinIO (S3 object store)  │                   │  Groq (LLM + vision)  │
    │  uploads + files buckets  │                   │  Qwen3 chat models    │
    │  :9000 (API) / :9001 (UI) │                   └───────────────────────┘
    └───────────────────────────┘

   Observability ── all services export OTLP traces + Micrometer metrics ──▶ Grafana LGTM (:3000)
                    AIProcessingService additionally exports LLM/RAG traces ──▶ Langfuse (:3001)
```

### Key Architectural Principles

1. **Event-Driven Architecture** — asynchronous communication via Kafka for loose coupling
2. **Transactional Outbox Pattern** — atomic DB write + event emission in a single transaction
3. **Change Data Capture (CDC)** — Debezium tails the Postgres WAL and publishes outbox rows to Kafka
4. **Database-per-service** — independent, dedicated databases; no shared schemas
5. **OAuth2 / OIDC security** — Keycloak; the gateway is a confidential **BFF** that relays tokens
6. **TUS protocol** — resumable, chunked uploads with checksum verification
7. **Full observability** — distributed tracing, metrics, logs (LGTM) + dedicated LLM tracing (Langfuse)

---

## 🔄 Event-Driven Pipeline

The system implements a **reliable, asynchronous, consistent** pipeline between the upload service
and the AI service. The happy path from upload to "document fully enriched & searchable":

```
1️⃣  FILE UPLOAD COMPLETION                       TusUploaderService
    BEGIN TX
      ├─ Save File + FileVersion (MERGE_PENDING)
      ├─ INSERT files_outbox_events { eventType: "FILE_INSERTED", payload: {fileId, version, …} }
      └─ INSERT files_outbox_events { eventType: "STORAGE_CONFIRMED", … }   ← quota accounting
    COMMIT
                            ▼
2️⃣  CHANGE DATA CAPTURE                           Debezium (pgoutput)
      └─ Tails WAL → unwrap → route → Kafka topic  file-outbox
                            ▼
3️⃣  AI CONSUMPTION                                AIProcessingService @KafkaListener("file-outbox")
      ├─ Create Job (PENDING) and acknowledge
      ├─ Download file from TusUploaderService (Bearer token)
      ├─ Analyze content → summary · tags · sensitivity
      │    • documents (PDF/DOCX/text) → text model
      │    • images                    → vision model
      └─ Publish "file-analyzed" (kicks off the embedding branch, independently)
                            ▼
4️⃣  AI RESULT PUBLICATION                         JobStatusService
    BEGIN TX
      ├─ Update Job (COMPLETED)
      └─ INSERT job_outbox { event_type: "ANALYSIS_COMPLETED", payload: { metadata } }
    COMMIT  →  Debezium  →  Kafka topic  job-outbox
                            ▼
5️⃣  METADATA UPDATE                               TusUploaderService @KafkaListener("job-outbox")
      └─ Update FileVersion with AI metadata (summary, tags, sensitivity)

  (parallel)  EMBEDDING BRANCH                     AIProcessingService
      └─ Extract text → chunk → embed (Qwen3-Embedding-4B) → store in pgvector vector_store
```

> **Ordering note:** AI **metadata** is committed (`ANALYSIS_COMPLETED` → `job_outbox`) *before* the
> embedding branch is kicked off (`file-analyzed`). The two run independently — embeddings never gate
> the metadata update.

### Event Flow Guarantees

✅ **Atomicity** — DB writes and outbox events share one transaction
✅ **Durability** — events are persisted before publication
✅ **At-least-once delivery** — Kafka guarantees delivery; consumers are idempotent
✅ **Ordering** — preserved per Kafka partition key
✅ **Fault tolerance** — retries at multiple levels + Dead Letter Topic for poison messages

---

## 🎯 Services Overview

### 1. ApiGateway (Port 8080) — Backend-for-Frontend

Spring **Cloud Gateway (MVC)** acting as a confidential **OIDC BFF client** (`gateway-client`). It
runs the Authorization-Code flow server-side, holds the **session cookie**, and **relays** the access
token (`TokenRelay`) to downstream services so the SPA never handles tokens directly.

**Responsibilities**
- Single entry point; OAuth2 login/logout against Keycloak (RP-initiated)
- Session + **CSRF** (`XSRF-TOKEN` cookie ↔ `X-XSRF-TOKEN` header)
- Token relay (Bearer) to all backend services
- Serves the SPA under `/ui` and exposes a session-introspection endpoint
- **Aggregated OpenAPI / Swagger UI** for all services

**Routes**
| Path | Target | Filter |
|------|--------|--------|
| `/api/v1/files/**`, `/api/v1/uploads/**`, `/api/v1/outbox/**` | TusUploaderService :8081 | TokenRelay |
| `/api/v1/users/**` | UserDetailService :8082 | TokenRelay |
| `/api/v1/jobs/**` | AIProcessingService :8083 | TokenRelay |
| `/api/rag/**` | AIProcessingService :8083 | TokenRelay |
| `/api/auth/userinfo` | Gateway-owned (current session) | — |
| `/ui`, `/ui/**` | Frontend (`UI_URL`) | — |
| `/docs/openapi/{tus,users,ai}`, `/v3/api-docs` | Aggregated OpenAPI | — |

> SockJS receive transports require incremental flushing, so the gateway sets
> `spring.cloud.gateway.mvc.streaming-media-types` for `text/event-stream` and
> `application/javascript` (charset included) — otherwise the realtime channel hangs.

### 2. TusUploaderService (Port 8081)

**Responsibilities**
- Resumable file uploads via the **TUS protocol** (creation, creation-with-upload, resumable, expiration, checksum, termination)
- File **versioning** and metadata management
- **MinIO** (S3) object storage
- **Sharing** scopes & per-file ACLs; **favorites**; **per-user storage quota** enforcement
- Outbox **publisher** (file lifecycle + storage events) and **consumer** of AI results

**Key Features**
- ✅ TUS chunked/resumable uploads with SHA-1 / MD5 checksum verification
- ✅ Content-Type auto-detection (Apache Tika)
- ✅ File versioning with per-version metadata
- ✅ Sharing scopes: `PERSONAL`, `DEPARTMENT`, `ORGANIZATION` + explicit user shares
- ✅ Favorites (per file version)
- ✅ Storage-quota reservation/confirmation/release via outbox events
- ✅ Scheduled cleanup of expired uploads ("WALL-E" task)

**Main endpoints**
- `POST/HEAD/PATCH/DELETE /api/v1/uploads[/{id}]` — TUS lifecycle
- `GET /api/v1/files` (list/filter), `GET /api/v1/files/{id}`, `…/download`, `…/preview`
- `GET/PATCH /api/v1/files/generatedmetadata/{id}` — AI metadata
- `GET/POST/PATCH/DELETE /api/v1/files/{id}/shares[/{shareId}]`, `GET /api/v1/files/my-shared`, `/my-uploads`, `POST /writable-ids`
- `POST/GET /api/v1/files/{fileId}/versions[/…]`, favorite/unfavorite, `GET /api/v1/files/favorites`

**Database:** `uploads`, `files`, `file_versions`, `file_shares`, `file_version_favorites`, `files_outbox_events`

### 3. UserDetailService (Port 8082)

**Responsibilities**
- User profile management synced with Keycloak (org / department / role)
- **Avatars** (upload / fetch / delete)
- **Per-user storage quota** bookkeeping (consumes storage outbox events idempotently)
- User search, listing, batch lookup

**Main endpoints**
- `GET /api/v1/users` (search/filter), `GET /api/v1/users/{id}`, `POST /api/v1/users/batch`
- `POST/PUT /api/v1/users[/{id}]`, `DELETE /api/v1/users/{id}`
- `GET /api/v1/users/me`, `POST/DELETE /api/v1/users/me/avatar`, `GET /api/v1/users/{id}/avatar`

**Database:** `users` (profile + org/department + `storageUsed` / `storageQuota`)

### 4. AIProcessingService (Port 8083)

**Responsibilities**
- **Document analysis** — AI metadata (summary, tags, sensitivity) for documents **and images (vision)**
- **Text extraction** (PDF/DOCX/text) → **semantic chunking** → **embedding generation** → **pgvector** storage
- **Conversational RAG** — natural-language Q&A over the user's accessible documents, **streamed** over STOMP
- **Embedding deletion** — event-driven cleanup when files/versions are removed
- **Job management** — pipeline state machine, retries, Dead Letter Topic
- **Realtime push** — per-user STOMP/SockJS channel for job status and RAG tokens
- **LLM observability** — Langfuse traces + optional evaluation scores

**Main endpoints**
- `POST /api/rag/query/stream` — start a streamed RAG query (answer pushed to `/user/queue/rag`)
- `POST /api/v1/jobs/status/batch` — job statuses for many file versions
- `GET /api/v1/jobs/stats` — dashboard counters
- `GET /api/v1/jobs/failed` — list failed jobs
- `POST /api/v1/jobs` — retry a failed job
- WebSocket (SockJS) endpoint behind the gateway for `/user/queue/jobs` and `/user/queue/rag`

**Database:** `jobs`, `job_outbox`, `vector_store` (pgvector), `file_version_embeddings`, `SPRING_AI_CHAT_MEMORY`

### 5. Frontend SPA (Port 5173 dev, served at `/ui`)

React 19 + TypeScript + Vite 6 + Tailwind v4 + Redux Toolkit single-page app. It does **not** run
the OIDC flow itself — it rides the gateway's BFF session (see [frontend/README.md](frontend/README.md)).

**Pages / features**
- **Dashboard** — at-a-glance counters (live, via STOMP)
- **Documents** — library + details, preview/download, AI metadata, versions
- **Upload** — app-global resumable TUS queue (persists in IndexedDB, survives navigation/relogin)
- **Shared with me** — documents shared by others
- **Favorites** — quick access + favorite download
- **Profile** — user info, avatar, storage usage bar
- **Admin** — organization overview + **user management** (roles, quotas)
- **Failed jobs** — inspect & retry failed AI jobs
- **RAG chat widget** — conversational Q&A with **streaming, markdown-rendered** answers + source citations

---

## 🤖 AI Metadata Generation

Uploaded files are analyzed with **Spring AI 1.1.2** against **Groq-hosted Qwen3** models. The
analyzer produces a structured summary, tags, and a sensitivity classification.

### Models

All chat/document/vision generation defaults to a single Groq-hosted model (configurable per role
via env vars), replacing the earlier dual-Llama strategy:

| Concern | Default model | Provider | Notes |
|---------|---------------|----------|-------|
| Text / PDF analysis | `qwen/qwen3.6-27b` (`CHAT_MODEL` / `PDF_MODEL`) | Groq | Document metadata extraction |
| **Image (vision)** analysis | `qwen/qwen3.6-27b` (`IMAGE_MODEL`) | Groq | Multimodal; routed to Groq explicitly |
| RAG chat | `qwen/qwen3.6-27b` (`GROQ_MODEL`) | Groq | Answer generation over retrieved context |

> Supported content types are centralized in `SupportedContentTypes`: **documents/text** are both
> *analyzable* and *text-extractable* (they feed the embedding pipeline), while **images** are
> *analyzable* via the vision model but produce no text to embed.

### PDF & Content Handling

- **PDF text extraction** via Apache PDFBox; DOCX/text handled by the extraction service.
- **Smart truncation** to respect token limits — keeps the **beginning (70%) and end (30%)** of long
  documents around a truncation marker, configurable via `ai.document.max-content-chars` /
  `ai.document.beginning-percentage`.

### Retry with Equal-Jitter Exponential Backoff

AI analysis retries transient failures (default attempts configurable). Between attempts it sleeps a
delay that is both **exponential** and **equal-jittered** to avoid thundering-herd retries:

```kotlin
val exponentialBackoff = (analysisBackoffBaseMs * (1L shl (attempt - 1))).coerceAtMost(analysisBackoffCapMs)
val equalJitter = if (exponentialBackoff > 0) {
    val half = exponentialBackoff / 2
    half + Random.nextLong(half)        // random in [backoff/2, backoff)
} else 0L
```

### Metadata Output

```kotlin
data class MetadataDTO(
    val summary: String?,                 // concise 2-sentence summary
    val tags: String?,                    // 5 comma-separated keywords
    val sensitivity: sensitivityLevel?,   // PUBLIC | INTERNAL | CONFIDENTIAL | HIGHLY_CONFIDENTIAL
)
```

### Job State Machine

```
PENDING ──▶ PROCESSING ──▶ COMPLETED       ✅ analysis succeeded
   │            │
   │            ├──────────▶ UNSUPPORTED    ⚠️  content type not analyzable / failed permanently
   │            └──────────▶ FAILED         ❌ transient error (will be retried)
   └─ FAILED ─(retries exhausted)─▶ DEAD_LETTERED   ☠️ routed to Dead Letter Topic
```

Every transition (including the two non-terminal progress pings) is **pushed live** to the owning
user over STOMP — see [Realtime Channel](#-realtime-channel-stompsockjs).

---

## 🧠 RAG (Retrieval-Augmented Generation) System

The RAG subsystem answers natural-language questions over **only the documents the authenticated user
can access** (owned + shared), with **conversational memory** and **token streaming**. It is built on
Spring AI's **modular RAG** (`RetrievalAugmentationAdvisor`).

> 📚 Deep dives: [`docs/rag-implementation.md`](docs/rag-implementation.md) (query side, memory,
> streaming, tracing) and [`docs/embedding-configuration.md`](docs/embedding-configuration.md)
> (how documents become vectors). This section is the summary.

### Pipeline

```
INGESTION (per uploaded document, event-driven)
  TextExtractionService → ChunkingService (TokenTextSplitter) → EmbeddingGenerator
    └─ embeddings via Qwen3-Embedding-4B (2560-dim) → pgvector  vector_store  + file_version_embeddings

QUERY (POST /api/rag/query/stream)
  RagUserContext.currentUserId()                  ← identity from JWT subject (never the request body)
  FileAccessService.getAccessibleFileIds(user)    ← owned + shared file ids  (empty ⇒ early return, no LLM call)
  FilterExpressionBuilder.in("fileId", …)         ← scope retrieval to those files
  RetrievalAugmentationAdvisor (top-K, optional query rewrite/expansion, similarity threshold)
  MessageWindowChatMemory  keyed "{userId}:{conversationId}"   ← per-user conversation isolation
  chatClient.prompt().advisors(retrieval, memory).stream()     ← tokens pushed to /user/queue/rag
```

### Embeddings

- **Model:** `Qwen3-Embedding-4B` → **2560-dimensional** vectors.
- **Backends (interchangeable, same vector space):** local **Ollama** (`qwen3-embedding:4b`) *or*
  **OpenWebUI** (OpenAI-compatible). Selected by `app.embedding.provider`.
- **Store:** pgvector table `vector_store`, column `embedding vector(2560)`.
- **Search:** **exact (sequential-scan) cosine similarity** — there is intentionally **no ANN index**,
  because 2560 dimensions exceed pgvector's 2000-dim limit for HNSW/IVFFlat.
- **Parallelism:** up to **3 concurrent** embedding calls (`min(topic partitions, consumer concurrency)`).

### Conversational Memory

- Per-conversation history persisted in Postgres (`SPRING_AI_CHAT_MEMORY`), keyed `"{userId}:{conversationId}"`
  so conversations are **isolated per authenticated user**.
- Follow-ups are compressed into standalone queries before retrieval; window size is configurable
  (`app.rag.memory.max-messages`, default 20).

### Streaming

Answers are pushed **token-by-token** over the per-user STOMP destination `/user/queue/rag`
(SockJS), correlated by a `streamId` returned immediately (`202 Accepted`). The same realtime
connection also carries job-status updates.

### Configuration (excerpt — all under `app.rag.*`)

| What | Property | Env | Default |
|------|----------|-----|---------|
| Chunks retrieved per query | `app.rag.retrieval.top-k` | `RAG_TOP_K` | `5` |
| Min cosine similarity | `app.rag.retrieval.similarity-threshold` | `RAG_SIMILARITY_THRESHOLD` | `0.0` (off) |
| Query rewrite | `app.rag.retrieval.query-rewrite` | `RAG_QUERY_REWRITE` | `true` |
| Query expansion | `app.rag.retrieval.query-expansion` | `RAG_QUERY_EXPANSION` | `false` |
| Conversation window | `app.rag.memory.max-messages` | `RAG_MEMORY_MAX_MESSAGES` | `20` |
| Serve streaming endpoint | `app.rag.streaming.enabled` | `RAG_STREAMING_ENABLED` | `true` |
| Eval scores (master switch) | `app.rag.scoring.enabled` | `RAG_SCORING_ENABLED` | `false` |

### Embedding Deletion

When a file/version is deleted, `TusUploaderService` emits a `FILE_DELETED` outbox event; CDC
publishes it to `file-outbox`; `AIProcessingService`'s deletion listener removes the corresponding
rows from `vector_store` and `file_version_embeddings`. The process is **idempotent** and resilient
to partial failures (one failed embedding does not block the rest).

---

## 📡 Realtime Channel (STOMP/SockJS)

AI-job status and RAG answers are delivered over a **single per-user STOMP-over-SockJS connection**
to `AIProcessingService`, routed through the gateway (no polling, no per-file streams).

- **Why SockJS:** the servlet gateway cannot proxy a raw WebSocket upgrade, so SockJS falls back to
  HTTP streaming transports, which the gateway *does* proxy (with `streaming-media-types` set).
- **`/user/queue/jobs`** — one message per job transition (`pending` / `processing` progress pings +
  the terminal `completed` / `unsupported` / `failed` / `deadlettered` outcome). A single
  subscription drives the live updates across **Library, Dashboard and Failed Jobs**.
- **`/user/queue/rag`** — `SOURCES → TOKEN… → DONE` (or `ERROR`) frames for a streamed query,
  correlated by `streamId`.

---

## 📦 Transactional Outbox Pattern

The **Transactional Outbox Pattern** guarantees that a database write and its corresponding event are
published atomically — solving the dual-write problem (DB commit vs. Kafka publish can't be made
atomic directly).

```kotlin
// ✅ SAFE: one transaction
@Transactional
fun completeUpload(upload: Upload) {
    database.save(upload)        // business write
    outbox.save(uploadEvent)     // event row in the SAME transaction
}                                // both commit or both roll back
// Debezium tails the outbox table and publishes to Kafka asynchronously
```

### Producers
- **TusUploaderService → `files_outbox_events`**: `FILE_INSERTED`, `FILE_DELETED`, and storage
  accounting events `STORAGE_RESERVED` / `STORAGE_CONFIRMED` / `STORAGE_RELEASED`.
- **AIProcessingService → `job_outbox`**: `ANALYSIS_COMPLETED` / `ANALYSIS_FAILED` / … with metadata.

### Debezium / CDC
- Connector class: `io.debezium.connector.postgresql.PostgresConnector` (plugin `pgoutput`).
- `ExtractNewRecordState` (unwrap) + `ByLogicalTableRouter` map each outbox table to a clean topic
  (`file-outbox`, `job-outbox`).
- **Publications are `filtered`** (`publication.autocreate.mode=filtered`) and scoped to the outbox
  tables only — a `FOR ALL TABLES` publication breaks DELETEs on tables without a primary key.
- Connectors are **registered automatically** by the compose `setup` profile (idempotent `PUT`,
  with auto-restart of failed tasks) — see [`just debezium`](#-configuration--setup).

### Benefits
✅ Atomic DB write + event · ✅ No lost events · ✅ Services don't depend on Kafka directly ·
✅ Built-in audit trail · ✅ Idempotent consumers tolerate duplicates

---

## 📈 Observability (LGTM + Langfuse)

Two complementary stacks, both run via Docker Compose:

### Grafana LGTM (system telemetry) — `grafana/otel-lgtm`
- **All four services** export **OTLP traces** + **Micrometer metrics** to the LGTM container
  (OTLP `:4318`). Distributed traces span gateway → service → DB → Kafka.
- **Grafana :3000** · Loki (logs) `:3100` · Tempo (traces) · Prometheus `:9099`.
- **Custom dashboards** auto-provisioned from `observability/`:
  HTTP RED + JVM/Hikari + the embedding pipeline, plus a logs dashboard.
- **Trace ↔ logs correlation** wired via `grafana-datasources.yaml` (Loki derived field parses
  `mdc_traceId`; Tempo `tracesToLogsV2` maps `service.name → application`).
- **Tempo idle period** raised to 15s (`observability/tempo-config.yaml`): cross-service spans arrive
  seconds apart, and the 1s default sealed blocks before the gateway root span landed
  (`<root span not yet received>`).

### Langfuse (LLM/RAG tracing) — self-hosted
- **Only AIProcessingService** traces to Langfuse — it's the only service doing LLM work. The OTel
  **trace id == the Langfuse trace id**, which is how evaluation scores are attached out-of-band.
- Spring AI emits `gen_ai.*` observations; a custom `LangfuseObservationFilter` adds full
  prompt/completion text; `RagTracing` / `PipelineTracing` tag traces with user/session and feature
  tags; `TraceContextInterceptor` recovers `traceparent` from Debezium payloads so the
  upload→embedding chain stays one connected trace.
- Optional **evaluation scores** (relevance, LLM-as-judge groundedness) posted via Langfuse REST.
- **Langfuse UI :3001** (self-hosted stack: web, worker, ClickHouse, Redis, MinIO, Postgres).

> 📚 See [`docs/langfuse-instrumentation.md`](docs/langfuse-instrumentation.md) and
> [`docs/langfuse-self-hosting.md`](docs/langfuse-self-hosting.md).

---

## 🛠️ Technology Stack

### Backend Services

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 1.9.25 | Primary language |
| **Java (JDK)** | 21 | Runtime / toolchain |
| **Spring Boot** | 3.5.8 / 3.5.9 (Gateway 3.4.12) | Microservice framework |
| **Spring Framework** | 6.2.15 (pinned) | Pinned to avoid a 6.2.14 startup deadlock |
| **Spring AI** | 1.1.2 | LLM, embeddings, modular RAG, chat memory |
| **Spring Security** | 6.x | OAuth2 resource server / OIDC client |
| **Spring Cloud Gateway (MVC)** | — | API gateway / BFF |
| **Spring Kafka** | — | Kafka integration |
| **Spring Data JPA + Flyway** | — | Persistence + migrations |
| **PostgreSQL** | 15 (+ `pgvector` for aireqdb) | Per-service databases & vector store |
| **Apache Kafka** | 4.1.0 | Event streaming |
| **Kafka Connect + Debezium** | cp-kafka-connect / pgoutput | Change Data Capture |
| **Apache PDFBox / POI / Tika** | — | PDF & DOCX text extraction / content-type detection |
| **MinIO** | latest | S3-compatible object storage |

### AI / Embeddings

| Component | Choice |
|-----------|--------|
| Chat / PDF / **vision** LLM | **Groq**-hosted Qwen3 (`qwen/qwen3.6-27b`, configurable) |
| Embedding model | **Qwen3-Embedding-4B** (2560-dim) via **Ollama** or **OpenWebUI** |
| Vector store | PostgreSQL **pgvector** (exact cosine search, no ANN index) |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| **React** | 19 | UI framework |
| **TypeScript** | 5.7 | Type-safe JS |
| **Vite** | 6 | Build tool / dev server |
| **Tailwind CSS** | 4 | Styling ("Corporate Precision" design tokens) |
| **Redux Toolkit** + react-redux | 2.x / 9.x | State management |
| **React Router** | 7 | Routing (`basename="/ui"`) |
| **Axios** | 1.7 | HTTP (cookies, XSRF, 401 redirect) |
| **@stomp/stompjs + sockjs-client** | 7.x / 1.6 | Realtime (jobs + RAG streaming) |
| **tus-js-client** | 4.x | Resumable uploads |
| **react-markdown + remark-gfm** | 10.x / 4.x | Markdown rendering in chat |
| **docx-preview** | 0.3 | In-browser DOCX preview |

### Infrastructure & Observability

| Technology | Version | Purpose |
|------------|---------|---------|
| **Docker Compose** | — | Local orchestration of all infra |
| **Keycloak** | 26.4 | OAuth2 / OIDC provider |
| **Kafka UI** | latest | Kafka cluster monitoring |
| **Grafana LGTM** | `grafana/otel-lgtm` | Metrics, logs, traces (Grafana/Loki/Tempo/Prometheus) |
| **Langfuse** | v3 (self-hosted) | LLM/RAG tracing & evaluation |

---

## 🧪 Testing Strategy

The backend uses **JUnit 5 + MockK / SpringMockK** with **Testcontainers** for integration/E2E; the
frontend uses **Vitest + React Testing Library** (jsdom). Tests cover the three levels — unit,
integration, and end-to-end.

| Service | Test files | Notable suites |
|---------|-----------:|----------------|
| **TusUploaderService** | 18 | Tus service/controller, file service, Kafka producer/consumer, JPA specs, Debezium/Kafka integration, E2E |
| **AIProcessingService** | 20 | RAG service, embedding generator/processor, text extraction, job status/stats/retry, dead-letter, conversation resolver, scoring, Kafka listener |
| **UserDetailService** | 10 | User service/controller, storage-outbox consumer, Keycloak-mock integration |
| **ApiGateway** | 1 | Context load / smoke |

> ℹ️ **Update vs. earlier docs:** AIProcessingService **is** covered by automated tests now (20 files),
> including RAG, embeddings and the job pipeline — the earlier "manual testing only" caveat no longer
> applies. AI calls and the vector store are mocked/contained so suites run without live Groq/Ollama.

**Backend**
```bash
./gradlew test                      # all tests for a service
./gradlew test jacocoTestReport     # with JaCoCo coverage
./gradlew test --tests "*unit*"     # filter by level (unit/integration/e2e)
```

**Frontend**
```bash
cd frontend
npm test            # vitest run
npm run test:watch  # watch mode
npm run typecheck   # tsc -b --noEmit  (use this for static checks; eslint config is minimal)
```

---

## ⚙️ Configuration & Setup

### Prerequisites
- Docker & Docker Compose
- Java 21 (JDK)
- Node.js 18+ (frontend)
- **Groq API key** (LLM + vision)
- **Ollama** (embeddings) — or an OpenWebUI endpoint
- *(optional)* [`just`](https://github.com/casey/just) for the convenience commands

### 1. Environment variables

`AIProcessingService/.env` holds the LLM + Langfuse config (kept out of `application.yml` defaults):

```bash
export GROQ_API_KEY=your-groq-api-key
export GROQ_BASE_URL=https://api.groq.com/openai
export GROQ_MODEL=qwen/qwen3.6-27b
export LANGFUSE_OTLP_ENDPOINT=http://localhost:4318/v1/traces   # LGTM by default; point to Langfuse to trace LLM there
export LANGFUSE_AUTH_HEADER=                                     # Basic auth header for Langfuse ingestion (if used)
```

Everything else (models per role, RAG knobs, embedding provider, ports) has sensible defaults in each
service's `application.yml` and can be overridden via the env vars documented above and in
[`docs/`](docs/).

> Get a free Groq key at <https://console.groq.com/>. Pull the embedding model with Ollama:
> ```bash
> ollama pull qwen3-embedding:4b
> ```

### 2. Start the infrastructure

```bash
docker compose up -d         # or:  just docker
docker compose ps            # check health
```

This brings up Postgres ×4 (filedb, aireqdb+pgvector, userdb, keycloak-db), MinIO, Keycloak, Kafka,
Kafka UI, Kafka Connect, the **LGTM** stack, and the **Langfuse** stack.

### 3. Register the Debezium connectors

```bash
just debezium
# equivalent to:  docker compose --profile setup up --wait setup-connector setup-job-outbox-connector
```

The setup containers `PUT` the connector configs idempotently and auto-restart failed tasks (no manual
`curl` needed).

### 4. Run the backend services (locally, via Gradle)

The four Spring Boot apps run on the host (the `spring-boot-docker-compose` integration wires them to
the compose infra). In four terminals:

```bash
cd ApiGateway          && ./gradlew bootRun     # :8080
cd TusUploaderService  && ./gradlew bootRun     # :8081
cd UserDetailService   && ./gradlew bootRun     # :8082
cd AIProcessingService && ./gradlew bootRun     # :8083
```

### 5. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

Then open **http://localhost:8080/ui** (not `:5173`). Opening `:5173` pre-login redirects to Keycloak;
after authenticating the gateway lands you at `:8080/ui`, same-origin with the BFF session. See
[frontend/README.md](frontend/README.md) for the BFF auth contract.

### Access points

| Service | URL |
|---------|-----|
| **Frontend (via gateway)** | http://localhost:8080/ui |
| **API Gateway / Swagger** | http://localhost:8080 · http://localhost:8080/swagger-ui.html |
| **Keycloak** | http://localhost:9090 |
| **Kafka UI** | http://localhost:8084 |
| **Kafka Connect REST** | http://localhost:8086 |
| **MinIO Console** | http://localhost:9001 |
| **Grafana (LGTM)** | http://localhost:3000 |
| **Langfuse** | http://localhost:3001 |
| **Prometheus** | http://localhost:9099 |

### Default users (Keycloak realm `file-management`)

| Username | Password | Role | Organization | Department |
|----------|----------|------|--------------|------------|
| admin | admin | admin | Company1 | IT |
| manager1 | password | manager | Company1 | HR |
| riccardo | password | staff | Company1 | IT |

---

## ✨ Features Implemented

### Core
- ✅ **Resumable uploads** (TUS): chunked, pause/resume/cancel, checksum verification, expiration cleanup; app-global queue persisted in IndexedDB (survives navigation & re-login)
- ✅ **File versioning** with per-version metadata and download
- ✅ **AI metadata**: summary, 5 tags, 4-level sensitivity — for documents **and images (vision)**
- ✅ **Conversational RAG chat**: per-user memory, file-access-scoped retrieval, **streamed** markdown answers + source citations
- ✅ **Event-driven architecture**: Transactional Outbox + Debezium CDC + Kafka, with Dead Letter Topic
- ✅ **Realtime updates**: per-user STOMP/SockJS channel for job status & RAG tokens
- ✅ **Security & access control**: Keycloak OAuth2/OIDC, gateway **BFF** (session + CSRF + TokenRelay), role-based (admin/manager/staff), org/department isolation
- ✅ **File sharing**: `PERSONAL` / `DEPARTMENT` / `ORGANIZATION` scopes + explicit user shares
- ✅ **Favorites** per file version

### Platform
- ✅ **Per-user storage quota**: admins set a byte quota; `storageUsed` tracks owned versions; enforced in TusUploaderService as soon as the TUS `Upload-Length` is known (deferred or at creation), rejecting over-quota uploads with `413 Payload Too Large`. Kept consistent via outbox `STORAGE_RESERVED` / `STORAGE_CONFIRMED` / `STORAGE_RELEASED` events, applied idempotently; quota can't be lowered below current usage. The profile page shows a used-vs-total bar.
- ✅ **User & admin management**: profiles, avatars, org overview, user/role/quota admin UI
- ✅ **Scheduled tasks**: expired-upload cleanup ("WALL-E"), pending-merge handling
- ✅ **Full observability**: OTLP traces + Micrometer metrics to Grafana **LGTM** (custom dashboards, trace↔logs correlation) + dedicated **Langfuse** LLM tracing with optional evaluation scores
- ✅ **Aggregated OpenAPI/Swagger** across all services at the gateway

---

## ⚠️ Known Limitations

1. **Asynchronous enrichment** — AI metadata and embeddings are produced after upload (seconds, depending on size/model). The UI reflects progress live via STOMP, but documents aren't searchable the instant they finish uploading.

2. **LLM token & rate limits** — large documents are truncated (beginning 70% + end 30%), so the middle of very large files may be under-represented. Groq's free tier is rate-limited; a paid tier or caching is advisable for heavier use.

3. **Vector search cost** — at 2560 dimensions there is no ANN index (exceeds pgvector's 2000-dim limit for HNSW/IVFFlat), so similarity search is an exact sequential scan. This is fine at project scale but would need a dimensionality strategy (or Matryoshka truncation) to scale to very large corpora.

4. **RAG retrieval window** — default `top-k = 5`; the similarity threshold ships at `0.0` (off) until Langfuse traces reveal the active embedding model's typical score distribution. No hybrid (BM25) search or cross-encoder reranking yet.

5. **Embedding deletion latency** — deletion is asynchronous (Kafka + CDC); there's no transactional coupling with the file delete, so vector cleanup may lag the file removal by a few seconds.

6. **Outbox growth** — `files_outbox_events` / `job_outbox` are not auto-pruned; a periodic cleanup job (e.g. delete rows older than N days) is recommended for long-running deployments.

7. **Dead Letter Topic** — poison messages are routed to a DLT, but there is no automated reprocessing or admin UI for DLT inspection; recovery is manual (e.g. via Kafka UI or by restarting a failed connector task).

---

## 📚 Additional Documentation

- [`docs/diagrams/architecture-flow.md`](docs/diagrams/architecture-flow.md) — end-to-end sequence + CDC topology (Mermaid)
- [`docs/rag-implementation.md`](docs/rag-implementation.md) — modular RAG, conversational memory, streaming, tracing
- [`docs/embedding-configuration.md`](docs/embedding-configuration.md) — embedding model, providers, parallelism
- [`docs/langfuse-instrumentation.md`](docs/langfuse-instrumentation.md) — OTel → Langfuse mapping & scoring
- [`docs/langfuse-self-hosting.md`](docs/langfuse-self-hosting.md) — running Langfuse locally
- [`frontend/README.md`](frontend/README.md) — BFF auth contract & SPA layout
- [`postman/`](postman/) · [`.postman/`](.postman/) — API collections
- [`keycloak/`](keycloak/) — realm exports

---

## 👥 Team

**Gruppo 11** — Politecnico di Torino
- Riccardo Mozzicato
- Iliyan Kostadinov
- Andrea Di Prima
- Samuele Scapellato

Web Applications II — A.Y. 2025/2026

---

## 📄 License

This project is part of an academic assignment for Politecnico di Torino.
