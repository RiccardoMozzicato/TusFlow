# Architecture flow: login → document availability

Two complementary views of the document portal. Both Mermaid blocks below render natively on
GitHub; pre-rendered images live alongside this file (`*.svg` / `*.png`).

- ![sequence](./flow-sequence.png) — `flow-sequence.svg` / `.png`
- ![topology](./cdc-topology.png) — `cdc-topology.svg` / `.png`

---

## 1. End-to-end sequence (login → availability)

> Note on ordering: AI **metadata** is committed (`ANALYSIS_COMPLETED` → `job_outbox`) *before* the
> embedding branch is even kicked off (`file-analyzed`). The two run independently — embeddings never
> gate the metadata update. See `MetadataProcessor.kt:342` (metadata) vs `:188` (publish file-analyzed).

```mermaid
sequenceDiagram
    autonumber
    actor U as User / SPA
    participant GW as ApiGateway :8080
    participant KC as Keycloak :9090
    participant TUS as TusUploader :8081
    participant S3 as MinIO
    participant FDB as filedb + outbox
    participant DBZ as Debezium
    participant K as Kafka
    participant AI as AIProcessing :8083
    participant ADB as aireqdb + pgvector

    rect rgb(225, 238, 255)
    Note over U,KC: 1 - LOGIN (OAuth2 / OIDC, BFF session cookie)
    U->>GW: GET /api/auth/userinfo
    GW-->>U: 401 anonymous
    U->>GW: GET /oauth2/authorization/gateway-client
    GW->>KC: authorization-code request
    KC-->>U: login page
    U->>KC: credentials
    KC-->>GW: redirect /login/oauth2/code + code
    GW->>KC: exchange code for tokens
    KC-->>GW: access + id token
    GW-->>U: Set-Cookie JSESSIONID + XSRF, redirect /ui
    Note over GW: TokenRelay injects Bearer token on every downstream hop
    end

    rect rgb(225, 248, 232)
    Note over U,FDB: 2 - UPLOAD (tus resumable + transactional outbox)
    U->>U: mint fileId UUID, persist to IndexedDB
    U->>GW: POST /api/v1/uploads [fileId, sharingScope]
    GW->>TUS: forward + Bearer
    TUS->>FDB: TX: INSERT uploads PENDING + outbox STORAGE_RESERVED
    TUS-->>U: 201 Location /uploads/{id}
    loop each chunk PATCH
        U->>GW: PATCH /api/v1/uploads/{id}
        GW->>TUS: forward
        TUS->>S3: put chunk into uploads bucket
    end
    TUS->>FDB: TX: INSERT files + file_versions MERGE_PENDING + outbox STORAGE_CONFIRMED + FILE_INSERTED
    TUS-->>U: 204 upload complete
    end

    rect rgb(255, 245, 224)
    Note over FDB,K: 3 - CDC (Debezium tails the WAL into Kafka)
    FDB-->>DBZ: logical replication of files_outbox_events
    DBZ->>K: route to topic file-outbox
    Note over K: also consumed by UserDetail :8082 - STORAGE_* updates storage-quota counter
    K-->>AI: FILE_INSERTED
    end

    rect rgb(248, 233, 252)
    Note over AI,ADB: 4 - AI METADATA, then EMBEDDINGS (independent branch)
    AI->>ADB: INSERT job PENDING
    AI->>TUS: GET /api/v1/files/{id}/download (direct, service token)
    TUS->>S3: lazy-merge parts into files bucket
    Note over TUS,FDB: merge sets file_versions = AVAILABLE (else EVE sweeper ~50min)
    TUS-->>AI: file bytes
    AI->>AI: extract text, Groq LLM, summary / tags / sensitivity
    AI->>ADB: markJobAsCompleted: outbox ANALYSIS_COMPLETED (metadata done first)
    AI-->>U: STOMP /user/queue/jobs : COMPLETED
    AI->>K: publishFileAnalyzedEvent to topic file-analyzed (only after metadata)
    K-->>AI: file-analyzed
    AI->>AI: chunk text, batch x5
    AI->>K: publish embeddings-batch
    K-->>AI: embeddings-batch
    AI->>AI: embed via Ollama qwen3-embedding:4b 2560-d
    AI->>ADB: store vectors in pgvector vector_store
    end

    rect rgb(225, 238, 255)
    Note over U,ADB: 5 - METADATA LOOPBACK (runs in parallel with embeddings, not after)
    ADB-->>DBZ: logical replication of job_outbox
    DBZ->>K: route to topic job-outbox
    K-->>TUS: ANALYSIS_COMPLETED
    TUS->>FDB: updateFileMetadata summary, tags, sensitivity
    U->>GW: GET /api/v1/files (open Library)
    GW->>TUS: forward
    TUS-->>U: files AVAILABLE + MERGE_PENDING, with ai metadata
    Note over U: row flips processing to ready, live via STOMP, no refetch
    end
```

---

## 2. Debezium / Kafka CDC topology

Two independent transactional-outbox pipelines (one per service DB) plus two internal AI topics for
the embedding fan-out.

```mermaid
flowchart LR
    subgraph TUSsvc["TusUploaderService :8081 - filedb"]
        TOUT[("files_outbox_events: STORAGE_*, FILE_INSERTED, FILE_DELETED")]
        TCONS["KafkaConsumer: updateFileMetadata"]
    end

    subgraph AIsvc["AIProcessingService :8083 - aireqdb / pgvector"]
        MP["MetadataProcessor"]
        FDL["FileDeletionListener"]
        EP["EmbeddingProcessor"]
        EG["EmbeddingGenerator to vector_store"]
        JOUT[("job_outbox: ANALYSIS_*")]
    end

    subgraph UDSsvc["UserDetailService :8082 - userdb"]
        SOC["StorageOutboxConsumer: storage quota"]
    end

    subgraph DBZ["Debezium / Kafka Connect"]
        C1{{"connector 1: filtered publication"}}
        C2{{"connector 2: filtered publication"}}
    end

    subgraph KAFKA["Kafka - KRaft"]
        T1[["file-outbox"]]
        T2[["job-outbox"]]
        T3[["file-analyzed"]]
        T4[["embeddings-batch"]]
    end

    TOUT -- WAL/CDC --> C1 --> T1
    T1 -- FILE_INSERTED --> MP
    T1 -- FILE_DELETED --> FDL
    T1 -- STORAGE_* --> SOC

    MP -- publish --> T3 --> EP
    EP -- publish --> T4 --> EG
    MP -- writes --> JOUT

    JOUT -- WAL/CDC --> C2 --> T2
    T2 -- ANALYSIS_COMPLETED --> TCONS
```