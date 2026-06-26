# Embedding Configuration & Parallelism (AIProcessingService)

How document embeddings are configured, which model runs where, and how to tune the
embedding throughput. This is the single reference for "where do I change the YAML?"

## TL;DR

- **Model:** `Qwen3-Embedding-4B` → **2560-dim** vectors.
- **Backends:** local **Ollama** (`qwen3-embedding:4b`) *or* **OpenWebUI** (OpenAI-compatible,
  `Qwen/Qwen3-Embedding-4B`). Both emit 2560-dim vectors in the same space, so they're interchangeable
  against the same `vector_store`.
- **Store:** pgvector table `vector_store`, column `embedding vector(2560)`, **no ANN index** — search
  is an exact sequential scan (2560 exceeds pgvector's 2000-dim HNSW limit).
- **Parallelism:** up to **3 concurrent** embedding calls — `min(topic partitions, consumer concurrency)`.

---

## 1. Where each knob lives

| What | Property | File | Env var | Default |
|------|----------|------|---------|---------|
| Ollama embedding model | `spring.ai.ollama.embedding.options.model` | `application.yml` | `OLLAMA_EMBEDDING_MODEL` | `qwen3-embedding:4b` |
| Ollama base URL | `spring.ai.ollama.base-url` | `application.yml` | `OLLAMA_BASE_URL` | `http://localhost:11434` |
| OpenWebUI embedding model | `spring.ai.openai.embedding.options.model` | `application-local.yaml` | `OPENWEBUI_EMBEDDING_MODEL` | `Qwen/Qwen3-Embedding-4B` |
| OpenWebUI base URL / key | `spring.ai.openai.base-url` / `.api-key` | `application-local.yaml` | `OPENWEBUI_BASE_URL` / `OPENWEBUI_API_KEY` | — |
| **Vector store dimensions** | `spring.ai.vectorstore.pgvector.dimensions` | `application.yml` | — | `2560` |
| **Provider toggle** (local profile only) | `app.embedding.provider` | `application.yml` | `APP_EMBEDDING_PROVIDER` | `openwebui` |
| **Concurrency** (parallel embed calls) | `app.embedding.concurrency` | `application.yml` | `EMBEDDING_CONCURRENCY` | `3` |
| Embedding-batch topic partitions | `EMBEDDINGS_BATCH_PARTITIONS` (const) | `KafkaConfig.kt` | — | `3` |
| Stored metadata: model name | `app.embedding.model` | `application.yml` | `EMBEDDING_MODEL_NAME` | `qwen3-embedding-4b` |
| Stored metadata: dimensions | `app.embedding.dimensions` | `application.yml` | `EMBEDDING_DIMENSIONS` | `2560` |
| Embed retry attempts | `ai.retry.embedding-attempts` | `application.yml` | — | `3` |
| Embed retry delay | `ai.retry.embedding-delay-ms` | `application.yml` | — | `60000` |
| Batch size (chunks per Kafka msg) | `batchSize` (local val) | `EmbeddingProcessor.kt` | — | `5` |
| PDFBox/FontBox log noise | `logging.level.org.apache.pdfbox` / `.fontbox` | `application.yml` | — | `ERROR` |

> `app.embedding.model` and `app.embedding.dimensions` are **informational** — they're written into each
> vector's metadata for auditing. They do **not** drive the actual model; the real model is
> `spring.ai.*.embedding.options.model` and the real width is `spring.ai.vectorstore.pgvector.dimensions`.
> Keep all of them in sync when you change the model.

---

## 2. Which model runs in which profile

Selection happens in **`conf/EmbeddingConfig.kt`** via a `@Primary` bean chosen by profile:

- **Non-`local` profiles (`dev`, `prod`, tests):** always **Ollama** (`qwen3-embedding:4b`).
- **`local` profile:** chosen by `app.embedding.provider` (env `APP_EMBEDDING_PROVIDER`):
  - `ollama` → Ollama on your Mac (`qwen3-embedding:4b`)
  - `openai` / `openwebui` → OpenWebUI (`Qwen/Qwen3-Embedding-4B`)
  - **Current default:** `openwebui`.

**Verify at startup** — `EmbeddingConfig` logs one INFO line naming the active backend, model, and dims:

```
Embedding model in use: provider=Ollama, model=qwen3-embedding:4b, dimensions=2560
Embedding model in use: profile=local, provider=openwebui (OpenWebUI), model=Qwen/Qwen3-Embedding-4B, dimensions=2560
```

---

## 3. Parallelism / throughput

The embedding pipeline:

```
file-analyzed  ──▶  EmbeddingProcessor  ──▶  embeddings-batch  ──▶  EmbeddingGenerator
                    (splits chunks into          (Kafka topic,           (consumes batches,
                     batches of 5)                3 partitions)           embeds each chunk)
```

Effective concurrency = **`min(topic partitions, consumer concurrency)`**. Three levers control it:

1. **Topic partitions** — `EMBEDDINGS_BATCH_PARTITIONS = 3` in `KafkaConfig.embeddingsBatchTopic()`.
   Without this bean the topic auto-creates with the broker default (**1 partition**), which hard-caps
   concurrency at 1 regardless of the setting below. KafkaAdmin raises an existing topic to this count
   on startup (it can only *increase* partitions, never decrease).
2. **Consumer concurrency** — `app.embedding.concurrency` (env `EMBEDDING_CONCURRENCY`, default `3`),
   read by `EmbeddingGenerator`'s `@KafkaListener(concurrency = ...)`.
3. **Null partition key** — `EmbeddingProcessor` sends batches with a **null key** so one document's
   batches spread across partitions (instead of all pinning to one). Safe because embedding batches
   have no ordering requirement (chunks dedupe by `(fileVersionId, chunkIndex)`).

**Within a single batch, chunks are embedded sequentially** — the parallelism is across batches only.

**Throttling for a single self-hosted GPU** (e.g. the 4B model on a Mac): set
`EMBEDDING_CONCURRENCY=1` (fully serial) or `2` to avoid saturating the backend. No code change, no redeploy.

---

## 4. Runbook: change the embedding model and/or dimensions

> ⚠️ Changing the **dimensionality** is **destructive** — existing vectors are a different size and
> can't be retyped in place, so they're cleared and documents must be **re-embedded** (re-upload or a
> re-embed run). This applies to **every** environment Flyway runs against, not just local.

1. **Make the model available** — `ollama pull <model>` and/or expose it in OpenWebUI. Confirm its
   native output dimensionality (Qwen3-Embedding: `0.6b`→1024, `4b`→2560, `8b`→4096).
2. **Point the backend(s) at it:**
   - Ollama: `spring.ai.ollama.embedding.options.model` in `application.yml`.
   - OpenWebUI: `spring.ai.openai.embedding.options.model` in `application-local.yaml`.
3. **Set the store width:** `spring.ai.vectorstore.pgvector.dimensions` in `application.yml` (must equal
   the model's output dims).
4. **Update the metadata block:** `app.embedding.model` and `app.embedding.dimensions`.
5. **Add a Flyway migration** (`V11__...sql`) that clears the old vectors and retypes the column — use
   `V10__Embedding_2560_qwen.sql` as the template:
   ```sql
   DELETE FROM file_version_embeddings;
   DELETE FROM vector_store;
   DROP INDEX IF EXISTS idx_vector_store_embedding_hnsw;
   ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(<dims>);
   ```
6. **Re-embed** the documents you care about.

**Dimensionality limits for an ANN index:** the pgvector `vector` type supports HNSW/IVFFlat only up to
**2000 dims**. Above that (as with 2560) you either accept **exact sequential scan** (current choice),
switch the column to **`halfvec(<dims>)` + `halfvec_cosine_ops`** (HNSW up to 4000 dims, needs Spring AI
halfvec support), or **MRL-truncate** the model output to ≤2000 dims to regain an index.

---

## 5. Migration reference — `V10__Embedding_2560_qwen.sql`

Migrated the store from `nomic-embed-text` (768) to `Qwen3-Embedding-4B` (2560):

- Clears `file_version_embeddings` then `vector_store` (incompatible 768-dim data).
- Drops the 768-bound HNSW index; deliberately does **not** recreate one (2560 > 2000-dim limit).
- `ALTER TABLE vector_store ALTER COLUMN embedding TYPE vector(2560)`.
- Updates the column COMMENT to note 2560 + exact-scan search.

---

## 6. Files involved

| File | Role |
|------|------|
| `src/main/resources/application.yml` | Ollama model, store dims, `app.embedding.*` (provider/concurrency/metadata), retry, logging |
| `src/main/resources/application-local.yaml` | OpenWebUI embedding model + endpoint (local profile) |
| `src/main/kotlin/.../conf/EmbeddingConfig.kt` | `@Primary` model selection per profile + startup log |
| `src/main/kotlin/.../conf/KafkaConfig.kt` | `embeddings-batch` topic declaration (partitions) |
| `src/main/kotlin/.../services/EmbeddingProcessor.kt` | Splits chunks into batches, publishes to `embeddings-batch` (null key) |
| `src/main/kotlin/.../services/EmbeddingGenerator.kt` | Consumes batches, embeds chunks, writes `vector_store` (`@KafkaListener` concurrency) |
| `src/main/resources/db/migration/V10__Embedding_2560_qwen.sql` | Destructive migration to `vector(2560)` |

---

## 7. Quick verification

```bash
# Active embedding model — look for the EmbeddingConfig line at startup:
#   "Embedding model in use: provider=..., model=..., dimensions=2560"

# Confirm the topic has 3 partitions (after a restart applies the bump):
kafka-topics --bootstrap-server localhost:9094 --describe --topic embeddings-batch

# Confirm a 2560-dim vector landed after embedding a document:
#   SELECT vector_dims(embedding) FROM vector_store LIMIT 1;   -- expect 2560
```
