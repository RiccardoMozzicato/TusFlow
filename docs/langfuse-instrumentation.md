# Langfuse Instrumentation (AIProcessingService)

How LLM/RAG operations are traced into Langfuse, the OpenTelemetry → Langfuse attribute mapping
this app relies on, and the best-practice audit behind the current setup. For running a local
Langfuse, see [`langfuse-self-hosting.md`](./langfuse-self-hosting.md).

## TL;DR

- Tracing is **OpenTelemetry-based**: Spring AI emits `gen_ai.*` observations, Micrometer →
  OTel bridge exports them over **OTLP** to Langfuse (or the local LGTM stack).
- Only **AIProcessingService** traces to Langfuse — it's the only service doing LLM work.
  ApiGateway / TusUploaderService / UserDetailService trace to **LGTM** by design (Langfuse is
  for LLM traces, not general HTTP spans).
- The OTel **trace id == the Langfuse trace id**, which is how evaluation scores are attached
  out-of-band via the REST API.
- Verified against the official guides:
  [Spring AI](https://langfuse.com/integrations/frameworks/spring-ai) +
  [OpenTelemetry mapping](https://langfuse.com/integrations/native/opentelemetry).

---

## 1. Wiring

| Concern | Where | Notes |
|---------|-------|-------|
| Dependencies | `AIProcessingService/build.gradle.kts` | `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-otlp`, `spring-boot-starter-actuator`; Spring AI BOM **1.1.2** |
| OTLP export | `application.yml` → `management.otlp.tracing` | `endpoint` = `${LANGFUSE_OTLP_ENDPOINT}` (default LGTM `:4318`), headers `Authorization: ${LANGFUSE_AUTH_HEADER}` + `x-langfuse-ingestion-version: "4"` |
| Sampling | `management.tracing.sampling.probability` | `1.0` (capture everything) |
| Baggage propagation | `management.tracing.baggage` | `remote-fields` + `tag-fields` = `langfuse.user.id`, `langfuse.session.id` |
| Reactive context propagation | `application.yml` → `spring.reactor.context-propagation` | `auto` — bridges trace/baggage into the streaming RAG `Flux` so the generation span keeps one trace and carries `langfuse.user.id` |
| Spring AI observations | `spring.ai.chat.observations` | `include-error-logging: true`; prompt/completion content added by the filter below |
| Duplicate-span suppression | `otel.instrumentation.spring-webmvc/spring-web` | disabled to avoid double HTTP spans |

### Custom tracing code (`…/tracing/`)

| Class | Responsibility |
|-------|----------------|
| `LangfuseObservationFilter` | Adds `gen_ai.prompt` / `gen_ai.completion` (full text) onto each ChatModel generation span — Spring AI omits content from spans otherwise. |
| `RagTracing` | Tags the RAG trace: `langfuse.trace.name=rag-query`, `langfuse.user.id`, `langfuse.session.id`, `langfuse.observation.input/output`; opens a child **"RAG Query"** span with retrieval stats; sets feature tags; sets user/session **baggage**; exposes `currentTraceId()` for scoring. The streaming path uses `traceStream` to open the same user-tagged `rag-query` root span across the async `Flux`, ended on the stream's terminal signal. |
| `PipelineTracing` | Tags the Kafka document pipeline stages (`upload-analysis`, `embedding-preparation`, `embedding-generation`) with model/file/chunk metadata, `langfuse.trace.name=document-analysis`, and tags. Every stage stamps `langfuse.user.id` explicitly (baggage doesn't ride the Kafka hop), so all observations group under the owner. |
| `LangfuseTags` | Helper that sets `langfuse.trace.tags` (a `string[]`) via the OTel API — Micrometer's `span.tag()` only does single strings. |
| `TraceContextInterceptor` | Recovers `traceparent` from Debezium outbox payloads (Debezium never injects Kafka headers) so the upload→embedding chain stays one connected trace. |

### Scoring (`…/rag/eval/`)

`RagScoringService` attaches evaluation **scores** to a finished query trace, async + best-effort,
gated by `app.rag.scoring.*`:

- **relevance** — mean retrieved-chunk similarity (cheap, deterministic).
- **groundedness** — `GroundednessEvaluator` LLM-as-judge (one extra LLM call; off by default).

`LangfuseScoreClient` POSTs to `{langfuse-base-url}/api/public/scores` using the OTel trace id and
the same `LANGFUSE_AUTH_HEADER`. This is a **separate REST path**, not OTLP.

---

## 2. OTel → Langfuse attribute mapping (the contract we rely on)

Trace-level (set on the root span, propagated to children via baggage):

| Langfuse property | OTel attribute |
|-------------------|----------------|
| trace name | `langfuse.trace.name` |
| user id | `langfuse.user.id` (or `user.id`) |
| session id | `langfuse.session.id` (or `session.id`) |
| tags | `langfuse.trace.tags` (`string[]`) |
| input / output | `langfuse.observation.input/output` on the root span |

Observation-level (per span):

| Langfuse property | OTel attribute |
|-------------------|----------------|
| input / output | `langfuse.observation.input/output`, or `gen_ai.prompt` / `gen_ai.completion` |
| model | `gen_ai.request.model` / `gen_ai.response.model` |
| token usage → cost | `gen_ai.usage.*` |
| generation type | any span carrying a model attribute is treated as a `generation` |

> Source: <https://langfuse.com/integrations/native/opentelemetry>. Langfuse increasingly filters
> per-observation, so trace-level ids must be on **every** span — hence the baggage setup (§3).

---

## 3. Baggage propagation

`RagTracing.withTraceBaggage()` opens OTel baggage for `langfuse.user.id` / `langfuse.session.id`
around the query. With `management.tracing.baggage.tag-fields` listing those keys, Micrometer
copies them as tags onto **every span created in scope** (the RAG Query span and the generation),
not just the root. `remote-fields` also serialises them into the W3C `baggage` header so they can
cross service boundaries.

This is the OTel-native equivalent of Langfuse's recommended `BaggageSpanProcessor` approach,
using Spring Boot's built-in Micrometer baggage support (no extra dependency).

---

## 4. Configuration knobs

| Property | Env | Default | Purpose |
|----------|-----|---------|---------|
| `management.otlp.tracing.endpoint` | `LANGFUSE_OTLP_ENDPOINT` | LGTM `…:4318/v1/traces` | Where spans go. Point at `…/api/public/otel/v1/traces` for Langfuse. |
| `…headers.Authorization` | `LANGFUSE_AUTH_HEADER` | _(empty)_ | `Basic base64(public:secret)` for Langfuse. |
| `app.rag.scoring.enabled` | `RAG_SCORING_ENABLED` | `true` | Master switch for eval scores. |
| `app.rag.scoring.relevance` | `RAG_SCORING_RELEVANCE` | `true` | Mean-similarity score. |
| `app.rag.scoring.groundedness` | `RAG_SCORING_GROUNDEDNESS` | `true` | LLM-judge score (extra LLM call). |
| `app.rag.scoring.langfuse-base-url` | `LANGFUSE_BASE_URL` | `https://cloud.langfuse.com` | REST host for scores (no `/api`). |

See [`langfuse-self-hosting.md` §4](./langfuse-self-hosting.md) for the exact local values.

---

## 5. Verify

1. Start Langfuse (self-hosting doc) and set `LANGFUSE_OTLP_ENDPOINT`, `LANGFUSE_BASE_URL`,
   `LANGFUSE_AUTH_HEADER` on AIProcessingService.
2. Run a RAG query and open the trace in Langfuse:
   - Trace name is **`rag-query`** (not the HTTP route); tags `rag`, `chat` present.
   - **User** and **Session** are populated (Sessions view groups the conversation).
   - The generation child span shows the model, prompt/completion, and token usage → cost.
   - `langfuse.user.id` / `langfuse.session.id` appear on the **child** spans too (baggage).
   - `relevance` (and `groundedness`, if enabled) scores are attached.
3. Upload a document and confirm a **`document-analysis`** trace with the
   analysis → embedding-preparation → embedding-generation spans linked into one trace.

---

## 6. Known limitations / future work

- **Baggage across Kafka**: `TraceContextInterceptor` propagates `traceparent` but not the
  `baggage` header, so user/session baggage doesn't ride the upload→embedding chain. Each pipeline
  stage (`PipelineTracing.tagUploadAnalysis`/`tagEmbeddingPreparation`/`tagEmbeddingBatch`) stamps
  `langfuse.user.id` explicitly instead — the embedding-generation stage reads it from chunk
  metadata since the batch event has no owner field.
- **Streaming RAG depends on Reactor context propagation**: `RagService.streamQuery` keeps its
  `rag-query` span open across the async `Flux` and relies on `spring.reactor.context-propagation=auto`
  to nest the Spring AI generation span under it. The root span is tagged with `langfuse.user.id`
  directly (not only via baggage), so user grouping holds even if propagation regresses; only the
  per-observation user tag on the generation child would be lost.
- **No PII masking**: prompts, answers, filenames, and queries are captured verbatim. Add a
  masking `ObservationFilter`/`SpanProcessor` if these traces may carry sensitive data.

## Gotcha: Docker Compose auto-wiring hijacks the OTLP endpoint

This service has `spring-boot-docker-compose` on the classpath. Spring Boot 3.5 recognizes the
**`grafana/otel-lgtm`** image in `docker-compose.yml` and auto-creates an OTLP-tracing
`ConnectionDetails` bean pointing at that container's `:4318`. **A `ConnectionDetails` bean
overrides the `management.otlp.tracing.endpoint` property** — so even with
`LANGFUSE_OTLP_ENDPOINT` (or the `application-local.yaml` pin) set to `:3001`, traces silently
went to LGTM and never reached Langfuse.

Fix: the `lgtm` service carries `labels: org.springframework.boot.ignore: true` (same mechanism
used for the app's databases). After changing the label you must **recreate the container**
(`docker compose up -d lgtm`) — Spring Boot reads the label from the running container, not just
the file — then restart the app.

Symptom/diagnosis: the app holds an established socket to `127.0.0.1:4318` and Langfuse shows 0
traces. Confirm with:
```bash
lsof -nP -a -p "$(lsof -nP -iTCP:8083 -sTCP:LISTEN | awk 'NR>1{print $2}')" -iTCP | grep -E ':3001|:4318'
```
Want it to read `:3001`, not `:4318`.

## Related docs

- [`langfuse-self-hosting.md`](./langfuse-self-hosting.md) — run Langfuse locally.
- [`rag-implementation.md`](./rag-implementation.md) — the RAG pipeline being traced.
- [`embedding-configuration.md`](./embedding-configuration.md) — embedding models/backends.
