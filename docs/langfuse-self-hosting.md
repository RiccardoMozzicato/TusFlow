# Self-Hosted Langfuse (docker-compose)

How to run Langfuse v3 locally as part of this project's `docker-compose.yml`, and how to
point **AIProcessingService** at it. This is the single reference for "how do I get RAG
traces into a local Langfuse?"

## TL;DR

- `docker compose up -d` brings up a full **self-hosted Langfuse v3** stack alongside the rest
  of the services.
- UI: **http://localhost:3001** — log in with `admin@local.dev` / `langfuse123`.
- An org/project/API-key pair is **auto-provisioned on first boot**, so traces flow without
  any manual UI setup.
- AIProcessingService runs on the **host** (outside compose) and reaches Langfuse via published
  ports. Set three env vars (see [§4](#4-wire-aiprocessingservice)) and you're done.
- All secrets here are **dev-only**. Do not reuse them anywhere real.

---

## 1. What gets started

Langfuse v3 is not a single container — it needs its own datastores. To avoid clashing with
the app's existing `postgres` / `minio`, every Langfuse service is prefixed `langfuse-` and
isolated on its own `langfuse-net` network:

| Service | Image | Role |
|---------|-------|------|
| `langfuse-web` | `langfuse/langfuse:3` | Web UI + ingestion API |
| `langfuse-worker` | `langfuse/langfuse-worker:3` | Async ingestion / evaluation worker |
| `langfuse-postgres` | `postgres:15` | Transactional metadata (own DB, not the app's) |
| `langfuse-clickhouse` | `clickhouse/clickhouse-server` | Trace/observation analytics store |
| `langfuse-redis` | `redis:7` | Queue + cache |
| `langfuse-minio` | `cgr.dev/chainguard/minio` | S3-compatible blob storage for events/media |

> **Why ClickHouse is left untagged:** Langfuse is version-sensitive to ClickHouse, so we
> follow the image tag the official Langfuse compose ships with rather than pinning our own.

---

## 2. Ports

Host ports were chosen to **not** collide with the rest of this stack (Grafana already owns
`3000`; the app's MinIO owns `9000/9001`).

| Host port | Service | Notes |
|-----------|---------|-------|
| **3001** | `langfuse-web` | The UI + OTLP/API endpoint. Stock Langfuse uses 3000; remapped to dodge Grafana. |
| 3030 | `langfuse-worker` | Bound to `127.0.0.1` only. |
| 8123 | `langfuse-clickhouse` | HTTP, `127.0.0.1` only. |
| 9010 | `langfuse-clickhouse` | Native protocol, `127.0.0.1` only (remapped off 9000). |
| 9190 / 9191 | `langfuse-minio` | S3 API / console. Remapped off 9090 (Keycloak owns `127.0.0.1:9090`). |
| 6379 | `langfuse-redis` | `127.0.0.1` only. |
| 5436 | `langfuse-postgres` | `127.0.0.1` only (remapped off 5432). |

---

## 3. Start / stop

```bash
# Start the whole stack (Langfuse included)
docker compose up -d

# Start only Langfuse-related services
docker compose up -d langfuse-web langfuse-worker langfuse-postgres \
  langfuse-clickhouse langfuse-redis langfuse-minio

# Tail the web logs (first boot runs DB + ClickHouse migrations — give it a minute)
docker compose logs -f langfuse-web

# Stop (keep data)
docker compose stop langfuse-web langfuse-worker

# Validate compose without starting anything
docker compose config >/dev/null && echo OK
```

First boot is slow: `langfuse-web` waits on Postgres + ClickHouse + Redis + MinIO healthchecks,
then runs migrations. The UI returns 5xx until migrations finish — that's expected.

---

## 4. Wire AIProcessingService

AIProcessingService runs on the **host**, so it talks to Langfuse over `localhost` + published
ports. It already reads these env vars (see `AIProcessingService/src/main/resources/application.yml`):

```bash
# OTLP trace export → Langfuse
LANGFUSE_OTLP_ENDPOINT=http://localhost:3001/api/public/otel/v1/traces

# REST base URL used by RAG evaluation scoring (no /api path)
LANGFUSE_BASE_URL=http://localhost:3001

# Basic auth = base64("<public-key>:<secret-key>")
LANGFUSE_AUTH_HEADER=Basic cGstbGYtbG9jYWwtZGV2OnNrLWxmLWxvY2FsLWRldg==

# Optional: turn on Langfuse evaluation scores for RAG queries
RAG_SCORING_ENABLED=true
```

The auth header is `base64` of the auto-provisioned key pair:

```
pk-lf-local-dev:sk-lf-local-dev  ->  cGstbGYtbG9jYWwtZGV2OnNrLWxmLWxvY2FsLWRldg==
```

Regenerate it if you change the keys:

```bash
printf '%s' 'pk-lf-local-dev:sk-lf-local-dev' | base64
```

> Without `LANGFUSE_OTLP_ENDPOINT` set, AIProcessingService defaults to the local **LGTM**
> stack (`http://localhost:4318/v1/traces`), *not* Langfuse. Setting the var above redirects
> traces to Langfuse.

---

## 5. Auto-provisioned credentials

`langfuse-web` is configured with `LANGFUSE_INIT_*` so a usable org/project/user + API keys
exist on first boot — no click-through onboarding.

| Thing | Value |
|-------|-------|
| Login email | `admin@local.dev` |
| Login password | `langfuse123` |
| Org | `local-org` (“Local”) |
| Project | `local-project` (“file-portal”) |
| Public key | `pk-lf-local-dev` |
| Secret key | `sk-lf-local-dev` |

---

## 6. Running alongside another Langfuse

Another project on the same Docker daemon may also create `langfuse-*` containers.
`container_name` is **global** to the daemon (not scoped per compose project), so this stack
prefixes its container names with **`g11-`** (`g11-langfuse-web`, `g11-langfuse-postgres`, …)
to avoid `"container name already in use"` errors — even when the other stack is only stopped.

**Host ports are also global**, but they only collide when **both** Langfuse stacks run at the
*same time*. This project assumes you run **one at a time**. If you ever need both up
concurrently, remap the overlapping host ports here (`3030`, `8123`, `9190/9191`, `6379`) or
drop their host mappings entirely (they're only needed for debugging).

---

## 7. Data & reset

Trace/analytics/blob data lives in named volumes (auto-prefixed by the compose project):

```
langfuse_postgres_data    langfuse_clickhouse_data    langfuse_clickhouse_logs
langfuse_minio_data       langfuse_redis_data
```

Wipe Langfuse completely (destroys all traces, projects, and the auto-provisioned keys):

```bash
docker compose down
docker volume rm \
  $(docker volume ls -q | grep -E 'langfuse_(postgres|clickhouse|minio|redis)') 2>/dev/null
docker compose up -d
```

---

## 8. Troubleshooting

| Symptom | Likely cause / fix |
|---------|--------------------|
| `Conflict. The container name "/langfuse-web" is already in use` | Another project's container. This stack uses the `g11-` prefix; ensure you pulled the latest compose. |
| `bind: address already in use` on `3001`/`9190`/`6379`/… | The other Langfuse (or another service) is running on that host port. Stop it, or remap (see [§6](#6-running-alongside-another-langfuse)). |
| UI returns 502/500 right after `up` | First-boot migrations still running. `docker compose logs -f langfuse-web` until you see it listening. |
| No traces appear in Langfuse | `LANGFUSE_OTLP_ENDPOINT` not set on AIProcessingService (still going to LGTM), or wrong `LANGFUSE_AUTH_HEADER`. |
| `401` from the ingestion/scoring API | Auth header doesn't match the project keys. Recompute base64 of `public:secret` ([§4](#4-wire-aiprocessingservice)). |
| App fails to start: `flyway … required a single bean, but 2 were found: flywayConnectionDetailsForG11Langfuse…` | Spring Boot's docker-compose support auto-created a `ConnectionDetails` bean for `langfuse-postgres`/`langfuse-clickhouse`. They carry `labels: org.springframework.boot.ignore: true` to prevent this (same as the app's own DBs) — make sure you pulled the latest compose. |

---

## ⚠️ Security note

Every secret in the Langfuse block of `docker-compose.yml` (`NEXTAUTH_SECRET=mysecret`,
`SALT=mysalt`, the zeroed `ENCRYPTION_KEY`, `langfuse123`, `miniosecret`, `myredissecret`,
ClickHouse/Postgres `postgres`/`clickhouse`) is a **local-development placeholder**, consistent
with the rest of this compose file. Never reuse these in a deployed environment — generate
fresh secrets and inject them via env/secret manager instead.

## Related docs

- [`rag-implementation.md`](./rag-implementation.md) — the RAG pipeline that produces the traces.
- [`embedding-configuration.md`](./embedding-configuration.md) — embedding model/backends.
- [`observability-changes.tex`](./observability-changes.tex) — OTel/observability write-up.
