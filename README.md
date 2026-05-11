# File Management System with AI-Powered Metadata Enrichment

**Gruppo 11** - Laboratorio 5  
**Web Applications II - Politecnico di Torino**

---

## 📋 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Event-Driven Pipeline](#event-driven-pipeline)
3. [RAG (Retrieval-Augmented Generation) System](#-rag-retrieval-augmented-generation-system)
4. [AI Metadata Generation](#ai-metadata-generation)
5. [Transactional Outbox Pattern](#transactional-outbox-pattern)
6. [Technology Stack](#technology-stack)
7. [Services Overview](#services-overview)
8. [Testing Strategy](#testing-strategy)
9. [Configuration & Setup](#configuration--setup)
10. [Features Implemented](#features-implemented)
11. [Known Limitations](#known-limitations)

---

## 🏗️ Architecture Overview

This system implements a **microservices-based file management platform** with **AI-powered metadata enrichment**. The architecture is built on an **event-driven foundation** using Apache Kafka and the **Transactional Outbox Pattern** to ensure reliable, asynchronous communication between services.

### System Components

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                          SYSTEM ARCHITECTURE                                │
└─────────────────────────────────────────────────────────────────────────────┘

┌──────────────────┐         ┌──────────────────┐         ┌──────────────────┐
│   Frontend       │         │   API Gateway    │         │   Keycloak       │
│   (React + TS)   │────────▶│   (Spring MVC)   │◀────────│   (OAuth2)       │
│   Port: 5173     │         │   Port: 8080     │         │   Port: 9090     │
└──────────────────┘         └──────────────────┘         └──────────────────┘
                                      │
                                      │ Token Relay
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐
    │ TusUploader     │    │ UserDetail      │    │ AIProcessing    │
    │ Service         │    │ Service         │    │ Service         │
    │ Port: 8081      │    │ Port: 8082      │    │ Port: 8083      │
    └────┬────────┬───┘    └────────┬────────┘    └────┬───────┬────┘
         │        │                  │                  │       │
         │        │                  │                  │       │
         │   ┌────▼──────────────────▼──────────────────▼───┐   │
         │   │          Apache Kafka + Debezium CDC         │   │
         │   │          (Event Streaming)                   │   │
         │   │          Port: 9094                          │   │
         │   └────┬──────────────────┬──────────────────┬───┘   │
         │        │                  │                  │       │
         │        │                  │                  │       │
    ┌────▼────────▼───┐    ┌─────────▼──────┐    ┌────▼───────▼────┐
    │ PostgreSQL      │    │ PostgreSQL     │    │ PostgreSQL      │
    │ (filedb)        │    │ (userdb)       │    │ (aireqdb)       │
    │ CDC enabled     │    │ Port: 5433     │    │ CDC enabled     │
    │ Port: 5432      │    └────────────────┘    │ Port: 5435      │
    └─────────────────┘                          └─────────────────┘
         │                                                │
         │ S3 API                                    Download files
         │                                                │
    ┌────▼────────────────────────────────────────────────▼─────┐
    │                    MinIO (Object Storage)                 │
    │                    S3-compatible API                      │
    │                    Port: 9000                             │
    └───────────────────────────────────────────────────────────┘
```

### Key Architectural Principles

1. **Event-Driven Architecture**: Asynchronous communication via Kafka for loose coupling
2. **Transactional Outbox Pattern**: Ensures atomic write operations and event publication
3. **Change Data Capture (CDC)**: Debezium monitors database changes and publishes to Kafka
4. **Microservices**: Independent, scalable services with dedicated databases
5. **OAuth2 Security**: Keycloak-based authentication and authorization
6. **TUS Protocol**: Resumable file uploads with chunking support

---

## 🔄 Event-Driven Pipeline

The system implements a sophisticated event-driven pipeline that ensures **reliable**, **asynchronous**, and **consistent** communication between the file upload service and the AI processing service.

### Pipeline Flow Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        EVENT-DRIVEN PIPELINE FLOW                           │
└─────────────────────────────────────────────────────────────────────────────┘

1️⃣  FILE UPLOAD COMPLETION
    ┌─────────────────────────────────────────────────────────────┐
    │ TusUploaderService                                          │
    │                                                             │
    │  BEGIN TRANSACTION                                          │
    │  ├─ Save FileVersion (status: MERGE_PENDING)               │
    │  ├─ Save Upload (status: MERGE_PENDING)                    │
    │  └─ INSERT into files_outbox_events                        │
    │     {                                                       │
    │       "eventType": "FILE_INSERTED",                        │
    │       "payload": {                                         │
    │         "fileId": "uuid",                                  │
    │         "version": 1,                                      │
    │         "filename": "document.pdf",                        │
    │         "contentType": "application/pdf",                  │
    │         "metadata": {...}                                  │
    │       }                                                    │
    │     }                                                      │
    │  COMMIT TRANSACTION                                         │
    └─────────────────────────────────────────────────────────────┘
                                ▼
2️⃣  CHANGE DATA CAPTURE (CDC)
    ┌─────────────────────────────────────────────────────────────┐
    │ Debezium Connector                                          │
    │                                                             │
    │  ├─ Reads PostgreSQL Write-Ahead Log (WAL)                 │
    │  ├─ Detects INSERT in files_outbox_events                  │
    │  ├─ Extracts event payload                                 │
    │  └─ Publishes to Kafka topic: file-outbox                  │
    └─────────────────────────────────────────────────────────────┘
                                ▼
3️⃣  AI PROCESSING SERVICE CONSUMPTION
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - KafkaListener                         │
    │                                                             │
    │  @KafkaListener(topics = ["file-outbox"])                  │
    │  ├─ Receives event from Kafka                              │
    │  ├─ Creates Job record (status: PENDING)                   │
    │  ├─ Acknowledges Kafka message (removes from queue)        │
    │  ├─ Downloads file from TusUploaderService                 │
    │  ├─ Selects appropriate AI model (PDF vs Generic)          │
    │  ├─ Analyzes file content (with retry logic)               │
    │  └─ Extracts metadata:                                     │
    │     • Summary (2 sentences)                                │
    │     • Tags (5 keywords)                                    │
    │     • Sensitivity (PUBLIC/INTERNAL/CONFIDENTIAL/...)       │
    └─────────────────────────────────────────────────────────────┘
                                ▼
4️⃣  AI RESULT PUBLICATION
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - JobStatusService                      │
    │                                                             │
    │  BEGIN TRANSACTION                                          │
    │  ├─ Update Job (status: COMPLETED)                         │
    │  └─ INSERT into job_outbox                                 │
    │     {                                                       │
    │       "event_type": "ANALYSIS_COMPLETED",                  │
    │       "payload": {                                         │
    │         "fileid": "uuid",                                  │
    │         "version": 1,                                      │
    │         "status": "COMPLETED",                             │
    │         "metadata": {                                      │
    │           "summary": "...",                                │
    │           "tags": "tag1,tag2,...",                         │
    │           "sensitivity": "CONFIDENTIAL"                    │
    │         }                                                  │
    │       }                                                    │
    │     }                                                      │
    │  COMMIT TRANSACTION                                         │
    └─────────────────────────────────────────────────────────────┘
                                ▼
5️⃣  METADATA UPDATE
    ┌─────────────────────────────────────────────────────────────┐
    │ TusUploaderService - KafkaConsumer                          │
    │                                                             │
    │  @KafkaListener(topics = ["job-outbox"])                   │
    │  ├─ Receives analysis results                              │
    │  ├─ Extracts enhanced metadata                             │
    │  └─ Updates FileVersion with AI-generated metadata         │
    │     (summary, tags, sensitivity)                           │
    └─────────────────────────────────────────────────────────────┘
```

### Event Flow Guarantees

✅ **Atomicity**: Database writes and outbox events are in the same transaction  
✅ **Durability**: Events are persisted before publication  
✅ **At-least-once delivery**: Kafka ensures message delivery (idempotency handles duplicates)  
✅ **Ordering**: Preserved through Kafka partitions  
✅ **Fault tolerance**: Retry mechanisms at multiple levels  

---

## 🧠 RAG (Retrieval-Augmented Generation) System

The system implements a **Retrieval-Augmented Generation (RAG)** pipeline to enable intelligent document search and question-answering capabilities. Users can query their uploaded documents using natural language, and the system retrieves relevant context from the document embeddings before generating accurate answers.

### RAG Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                     RAG PIPELINE ARCHITECTURE                               │
└─────────────────────────────────────────────────────────────────────────────┘

1️⃣  FILE UPLOAD & ANALYSIS
    ┌─────────────────────────────────────────────────────────────┐
    │ TusUploaderService                                          │
    │  └─> Uploads file to MinIO                                 │
    │  └─> Publishes FILE_INSERTED event to Kafka                │
    └─────────────────────────────────────────────────────────────┘
                                ▼
2️⃣  METADATA EXTRACTION
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - MetadataProcessor                     │
    │  └─> Downloads file                                         │
    │  └─> AI generates: summary, tags, sensitivity              │
    │  └─> Publishes FILE_ANALYZED event to Kafka                │
    └─────────────────────────────────────────────────────────────┘
                                ▼
3️⃣  TEXT EXTRACTION & CHUNKING
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - EmbeddingProcessor                    │
    │  └─> TextExtractionService: Extracts text from file        │
    │  └─> ChunkingService: Splits into semantic chunks          │
    │      • Chunk size: ~500 tokens                             │
    │      • Overlap: 50 tokens (preserves context)              │
    │  └─> Publishes batches to embeddings-batch topic           │
    └─────────────────────────────────────────────────────────────┘
                                ▼
4️⃣  EMBEDDING GENERATION
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - EmbeddingGenerator                    │
    │  └─> Ollama nomic-embed-text model (768 dimensions)        │
    │  └─> Generates vector embeddings for each chunk            │
    │  └─> Stores in PostgreSQL pgvector:                        │
    │      • vector_store: actual embeddings                     │
    │      • file_version_embeddings: tracking table             │
    └─────────────────────────────────────────────────────────────┘
                                ▼
5️⃣  RAG QUERY
    ┌─────────────────────────────────────────────────────────────┐
    │ User Query: "What are the main topics in my documents?"     │
    │                                                             │
    │ RagService                                                  │
    │  └─> 1. Convert query to embedding                         │
    │  └─> 2. Vector similarity search (HNSW index)              │
    │       • Filter by ownerId (user isolation)                 │
    │       • TopK: 5 most similar chunks                        │
    │  └─> 3. Retrieve relevant document chunks                  │
    │  └─> 4. Build context prompt with retrieved chunks         │
    │  └─> 5. LLM generates answer using context                 │
    │  └─> 6. Return answer + source citations                   │
    └─────────────────────────────────────────────────────────────┘
```

### RAG Components

#### 1. Text Extraction & Chunking

**TextExtractionService**
- Extracts plain text from various formats:
  - **PDF**: Apache PDFBox
  - **Text files**: Direct UTF-8 reading
  - **Other formats**: Extensible for future support

**ChunkingService**
- Uses Spring AI's `TokenTextSplitter`
- Configuration:
  ```yaml
  chunk-size: 500 tokens (~2000 characters)
  overlap: 50 tokens (10% overlap for context preservation)
  ```
- Each chunk includes metadata:
  - `fileId`, `fileVersionId`, `version`
  - `ownerUserId` (for access control)
  - `filename`, `contentType`
  - `summary`, `tags`, `sensitivity` (from AI analysis)
  - `chunkIndex`, `chunkTokens`

#### 2. Embedding Generation

**Model**: Ollama `nomic-embed-text`
- **Dimensions**: 768
- **Strengths**: 
  - High quality embeddings for semantic search
  - Optimized for retrieval tasks
  - Lower memory footprint than larger models

**Vector Store**: PostgreSQL with pgvector extension
- **Index Type**: HNSW (Hierarchical Navigable Small World)
  - Fast approximate nearest neighbor search
  - Optimized for large-scale similarity search
- **Distance Metric**: Cosine similarity
- **Schema**:
  ```sql
  CREATE TABLE vector_store (
    id UUID PRIMARY KEY,
    content TEXT,
    metadata JSONB,
    embedding vector(768)
  );
  
  CREATE INDEX ON vector_store USING hnsw (embedding vector_cosine_ops);
  ```

**Tracking Table**: `file_version_embeddings`
```sql
CREATE TABLE file_version_embeddings (
  id BIGSERIAL PRIMARY KEY,
  file_version_id BIGINT NOT NULL,
  chunk_index INTEGER NOT NULL,
  vector_store_id UUID NOT NULL,
  content_preview TEXT,
  chunk_tokens INTEGER,
  created_at TIMESTAMP DEFAULT NOW()
);
```

#### 3. RAG Query Service

**RagService.kt**
```kotlin
fun queryWithRag(userQuery: String, filters: Map<String, String>, topK: Int = 5): RagResponse {
    // 1. Build filter expression (user isolation)
    val filterExpression = FilterExpressionBuilder()
        .eq("ownerUserId", filters["ownerId"])
        .build()
    
    // 2. Semantic search with filters
    val searchRequest = SearchRequest.builder()
        .topK(topK)
        .filterExpression(filterExpression)
        .build()
    
    // 3. Retrieve relevant document chunks
    val documents = vectorStore.similaritySearch(searchRequest)
    
    // 4. LLM generates answer with retrieved context
    val result = chatClient.prompt()
        .advisors(
            QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build()
        )
        .user(userQuery)
        .call()
        .content()
    
    return RagResponse(answer = result, sources = documents)
}
```

**API Endpoint**: `POST /api/rag/query`
```json
{
  "query": "What are the main security policies in my documents?",
  "filters": {
    "ownerId": "user-uuid"
  },
  "topK": 5
}
```

**Response**:
```json
{
  "query": "What are the main security policies?",
  "answer": "Based on your documents, the main security policies include...",
  "sourcesCount": 5,
  "sources": [
    {
      "content": "Full chunk text...",
      "contentPreview": "Security policies require...",
      "metadata": {
        "fileId": "...",
        "filename": "security-policy.pdf",
        "chunkIndex": 3,
        "similarity": 0.87
      },
      "similarity": 0.87
    }
  ]
}
```

### Embedding Deletion System

When a file is deleted, the system automatically removes associated embeddings to maintain consistency and avoid orphaned data.

#### Deletion Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    EMBEDDING DELETION PIPELINE                              │
└─────────────────────────────────────────────────────────────────────────────┘

1️⃣  USER DELETES FILE
    ┌─────────────────────────────────────────────────────────────┐
    │ DELETE /api/v1/files/{id}                                   │
    │                                                             │
    │ FileService.deleteFile()                                    │
    │  BEGIN TRANSACTION                                          │
    │  ├─> Retrieve fileVersionIds                               │
    │  ├─> INSERT into files_outbox_events                       │
    │  │   {                                                      │
    │  │     "eventType": "FILE_DELETED",                        │
    │  │     "payload": {                                        │
    │  │       "fileId": "uuid",                                 │
    │  │       "fileVersionIds": [101, 102],                     │
    │  │       "deletedAt": "2025-01-06T...",                   │
    │  │       "deletedBy": "user-uuid"                          │
    │  │     }                                                   │
    │  │   }                                                      │
    │  ├─> fileRepository.deleteById(id)                         │
    │  └─> storageService.deleteAllVersions(id)                  │
    │  COMMIT TRANSACTION                                         │
    └─────────────────────────────────────────────────────────────┘
                                ▼
2️⃣  CDC PUBLISHES EVENT
    ┌─────────────────────────────────────────────────────────────┐
    │ Debezium Connector                                          │
    │  └─> Detects INSERT in files_outbox_events                 │
    │  └─> Publishes to Kafka topic: file-outbox                 │
    └─────────────────────────────────────────────────────────────┘
                                ▼
3️⃣  EMBEDDING DELETION
    ┌─────────────────────────────────────────────────────────────┐
    │ AIProcessingService - FileDeletionListener                  │
    │                                                             │
    │  @KafkaListener(topics = ["file-outbox"])                  │
    │  ├─> Filter event: event_type == "FILE_DELETED"           │
    │  ├─> Extract fileVersionIds from payload                   │
    │  └─> For each fileVersionId:                               │
    │      └─> EmbeddingDeletionService.deleteEmbeddings()       │
    └─────────────────────────────────────────────────────────────┘
                                ▼
4️⃣  VECTOR STORE CLEANUP
    ┌─────────────────────────────────────────────────────────────┐
    │ EmbeddingDeletionService                                    │
    │                                                             │
    │  └─> Query: SELECT * FROM file_version_embeddings          │
    │       WHERE file_version_id = ?                            │
    │  └─> For each embedding record:                            │
    │      ├─> vectorStore.delete(vectorStoreId)                 │
    │      └─> embeddingRepository.deleteById(embeddingId)       │
    │  └─> Result: All embeddings removed                        │
    └─────────────────────────────────────────────────────────────┘
```

#### Deletion Components

**FileDeletedEvent.kt**
```kotlin
data class FileDeletedEvent(
    val fileId: UUID,
    val deletedAt: String,
    val deletedBy: UUID?,
    val fileVersionIds: List<Long>
)
```

**FileDeletionListener.kt**
```kotlin
@KafkaListener(topics = ["file-outbox"], groupId = "embedding-deletion-group")
fun processFileDeletion(message: String, acknowledgment: Acknowledgment) {
    val wrapper = objectMapper.readValue(message, OutboxEventWrapper::class.java)
    
    // Filter only FILE_DELETED events
    if (wrapper.event_type != "FILE_DELETED") {
        acknowledgment.acknowledge()
        return
    }
    
    val event = objectMapper.convertValue(wrapper.payload, FileDeletedEvent::class.java)
    
    // Delete embeddings for each version
    event.fileVersionIds.forEach { fileVersionId ->
        embeddingDeletionService.deleteEmbeddingsByFileVersionId(fileVersionId)
    }
    
    acknowledgment.acknowledge()
}
```

**EmbeddingDeletionService.kt**
```kotlin
@Transactional
fun deleteEmbeddingsByFileVersionId(fileVersionId: Long) {
    val embeddings = embeddingRepository.findByFileVersionIdOrderByChunkIndex(fileVersionId)
    
    log.info("Deleting ${embeddings.size} embeddings for fileVersion $fileVersionId")
    
    embeddings.forEach { embedding ->
        try {
            // Delete from vector store
            vectorStore.delete(listOf(embedding.vectorStoreId.toString()))
            
            // Delete tracking record
            embeddingRepository.delete(embedding)
        } catch (e: Exception) {
            log.error("Failed to delete embedding ${embedding.id}: ${e.message}")
            // Continue with other embeddings
        }
    }
    
    log.info("Completed deletion for fileVersion $fileVersionId")
}
```

#### Deletion Guarantees

✅ **Event-Driven**: Asynchronous deletion using Kafka + CDC  
✅ **Transactional**: File deletion and outbox event are atomic  
✅ **Resilient**: Continues even if individual embeddings fail  
✅ **Consistent**: Ensures vector store and tracking table stay in sync  
✅ **Auditable**: All deletion events are logged  

#### Idempotency Handling

The deletion process is idempotent:
- If an embedding is already deleted, the operation silently succeeds
- Duplicate Kafka messages won't cause errors
- Partial failures are handled gracefully

### RAG Configuration

**application.yml** (AIProcessingService)
```yaml
spring:
  ai:
    vectorstore:
      pgvector:
        index-type: hnsw               # Fast approximate nearest neighbor
        distance-type: cosine_distance # Cosine similarity for semantic search
        dimensions: 768                # nomic-embed-text embedding size
        initialize-schema: false       # Managed by Flyway
        table-name: vector_store
    ollama:
      base-url: http://localhost:11434
      embedding:
        options:
          model: nomic-embed-text      # Embedding model
```

**Text Splitter Configuration**
```yaml
text-splitter:
  chunk-size: 500      # Tokens per chunk
  overlap: 50          # Token overlap between chunks
```

### RAG Limitations

1. **Asynchronous Processing**
   - Embeddings are not available immediately after upload
   - Processing time: ~2-10 seconds depending on file size

2. **Query Context Window**
   - TopK default: 5 chunks
   - Each chunk: ~500 tokens
   - Total context: ~2,500 tokens max

3. **Embedding Model Limitations**
   - Maximum text length: ~512 tokens per chunk
   - Language support: Primarily English (nomic-embed-text)

4. **Deletion Latency**
   - Embedding deletion is asynchronous
   - May take a few seconds to complete
   - No impact on file deletion completion

### RAG Future Enhancements

🔮 **Planned Improvements:**

1. **Hybrid Search**: Combine vector search with keyword (BM25) search
2. **Reranking**: Use cross-encoder model to rerank retrieved chunks
3. **Query Expansion**: Automatically expand user queries with synonyms
4. **Citation Links**: Direct links to source documents in responses
5. **Conversation History**: Multi-turn conversations with context
6. **Embedding Caching**: Cache frequently accessed embeddings
7. **Bulk Deletion Optimization**: Batch delete operations for performance

---

## 🤖 AI Metadata Generation

The system uses **Spring AI** framework with **Groq's LLaMA models** to automatically analyze uploaded files and generate enriched metadata.

### AI Models Configuration

The system employs a **dual-model strategy** based on content type:

| Content Type | AI Model | Provider | Use Case |
|--------------|----------|----------|----------|
| **PDF Files** | Llama 3.3 70B Versatile | Groq | Complex documents requiring deep analysis |
| **Text/Other** | Llama 3.1 8B Instant | Groq | Generic text files, faster processing |

### Model Selection Logic

```kotlin
private fun selectAIClient(contentType: String): ChatClient {
    // Extract base MIME type (remove charset and parameters)
    val baseMimeType = contentType.split(";").firstOrNull()?.trim() ?: contentType
    
    return if (baseMimeType.equals("application/pdf", ignoreCase = true)) {
        // PDF-specific model: More powerful for complex documents
        pdfAnalysisChatClient  // Llama 3.3 70B
    } else {
        // Generic model: Faster for simple text files
        chatClient  // Llama 3.1 8B
    }
}
```

### PDF Text Extraction

PDF files are processed using **Apache PDFBox**:

```kotlin
private fun extractTextFromPdf(inputStream: InputStream): String {
    var document: org.apache.pdfbox.pdmodel.PDDocument? = null
    return try {
        document = org.apache.pdfbox.pdmodel.PDDocument.load(inputStream)
        val stripper = org.apache.pdfbox.text.PDFTextStripper()
        val text = stripper.getText(document)
        log.info("Extracted ${text.length} chars from PDF (${document.numberOfPages} pages)")
        text
    } finally {
        document?.close()
    }
}
```

### Intelligent Content Truncation

To avoid AI token limits, the system implements **smart truncation**:

```kotlin
private fun truncateContent(content: String, maxChars: Int): String {
    if (content.length <= maxChars) return content
    
    // Take beginning (70%) and ending (30%) to preserve context
    val beginChars = (maxChars * beginningPercentage / 100.0).toInt()
    val endChars = maxChars - beginChars
    
    val beginning = content.take(beginChars)
    val ending = content.takeLast(endChars)
    
    val truncationMarker = "\n\n[... CONTENT TRUNCATED - ${content.length - maxChars} characters omitted ...]\n\n"
    
    return beginning + truncationMarker + ending
}
```

**Configuration** (application.yml):
```yaml
ai:
  document:
    max-content-chars: 15000      # ~3750 tokens
    beginning-percentage: 70       # 70% from start, 30% from end
```

### AI Analysis Prompt

The system uses a structured prompt template (`prompts/file-analysis-prompt.st`):

```
# AI File Analyzer - Document Metadata Extraction

## Your Role
You are an expert document analyzer for a corporate file management system.

## Instructions
Analyze the document and extract:

1. **Summary**: Concise summary (max 2 sentences)
2. **Tags**: Exactly 5 relevant keywords (lowercase, comma-separated)
3. **Sensitivity**: Classification level
   - PUBLIC: Can be shared publicly
   - INTERNAL: Internal company info, not confidential
   - CONFIDENTIAL: Sensitive business information
   - HIGHLY_CONFIDENTIAL: Trade secrets, legal docs

## Output Format (JSON only, no markdown):
{
  "summary": "Your 2-sentence summary",
  "tags": "tag1,tag2,tag3,tag4,tag5",
  "sensitivity": "PUBLIC|INTERNAL|CONFIDENTIAL|HIGHLY_CONFIDENTIAL"
}

## Document:
{document}
```

### Retry Logic with Exponential Backoff

AI analysis includes robust retry mechanism (max 5 attempts):

```kotlin
private fun analyzeUploadWithRetry(
    inputStream: InputStream, 
    jobId: UUID, 
    client: ChatClient,
    contentType: String
): MetadataDTO {
    val maxRetries = 5
    
    for (attempt in 1..maxRetries) {
        try {
            // Reset stream for retries
            if (attempt > 1 && inputStream.markSupported()) {
                inputStream.reset()
            }
            
            val result = analyzeUpload(inputStream, client, contentType)
            
            // Validate completeness
            if (result.summary == null || result.tags == null || result.sensitivity == null) {
                throw IllegalStateException("Incomplete metadata from AI")
            }
            
            return result  // ✅ Success
            
        } catch (e: Exception) {
            if (attempt < maxRetries) {
                // Exponential backoff: 1s, 2s, 3s, 4s, 5s
                val delayMs = (1000L * attempt).coerceAtMost(5000L)
                Thread.sleep(delayMs)
            }
        }
    }
    
    // ❌ All attempts failed - mark as UNSUPPORTED
    throw AIAnalysisUnsupportedException("AI analysis failed after $maxRetries attempts")
}
```

### Metadata Output Structure

```kotlin
data class MetadataDTO(
    val summary: String?,           // "This document outlines the Q4 financial results..."
    val tags: String?,              // "finance,quarterly,report,2024,revenue"
    val sensitivity: sensitivityLevel?  // CONFIDENTIAL
)

enum class sensitivityLevel {
    PUBLIC,
    INTERNAL,
    CONFIDENTIAL,
    HIGHLY_CONFIDENTIAL
}
```

### Job Status Management

AI processing jobs transition through well-defined states:

```
PENDING → COMPLETED     ✅ AI analysis successful
PENDING → UNSUPPORTED   ⚠️  AI failed after 5 retries (file still stored)
PENDING → FAILED        ❌ Processing error (will retry after 5 minutes)
FAILED  → FAILED        ❌ Retry failed → Dead Letter Topic (DLT)
```

---

## 📦 Transactional Outbox Pattern

The **Transactional Outbox Pattern** is a critical architectural pattern that ensures **reliable event publishing** in distributed systems. It guarantees that database writes and event emissions happen atomically.

### Why Transactional Outbox?

**Problem without Outbox:**
```kotlin
// ❌ DANGEROUS: Not atomic!
fun completeUpload(upload: Upload) {
    database.save(upload)           // Step 1: Write to DB
    kafka.publish(uploadEvent)      // Step 2: Publish event
    
    // What if Kafka is down? Event lost!
    // What if DB commit fails after Kafka publish? Inconsistent state!
}
```

**Solution with Outbox:**
```kotlin
// ✅ SAFE: Atomic operation
@Transactional
fun completeUpload(upload: Upload) {
    database.save(upload)           // Step 1: Write to DB
    outbox.save(uploadEvent)        // Step 2: Write event to outbox table
    // Both succeed or both fail together!
}
// Debezium reads outbox table and publishes to Kafka asynchronously
```

### Implementation Details

#### 1. TusUploaderService Outbox

**Entity:** `FilesOutboxEvent.kt`
```kotlin
@Entity
@Table(name = "files_outbox_events")
class FilesOutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,
    
    @Column(name = "event_type", nullable = false)
    var eventType: String,  // "FILE_INSERTED"
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    var payload: FileUploadEvent,  // Contains file metadata
    
    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    var createdAt: Date
)
```

**Transactional Write:** `TusService.kt`
```kotlin
@Transactional
fun appendChunk(dto: UploadDTO, content: ByteArray): UploadDTO {
    // ... file processing logic ...
    
    if (status == UploadStatus.MERGE_PENDING) {
        // 1. Save file version
        val version = fileService.createFileVersion(uploadEntity, file)
        
        // 2. Create outbox event (same transaction!)
        val fileUploadEvent = FileUploadEvent(
            fileId = uploadEntity.fileId,
            fileVersionId = version.id.toString(),
            version = version.version,
            ownerUserId = uploadEntity.ownerId,
            filename = version.filename,
            contentType = version.contentType,
            metadata = version.metadata
        )
        
        val outboxEvent = FilesOutboxEvent(
            eventType = "FILE_INSERTED",
            payload = fileUploadEvent,
            createdAt = Date.from(Instant.now())
        )
        
        outboxRepository.save(outboxEvent)
        // ✅ Transaction guarantees atomicity
    }
    
    return uploadEntity.toDTO()
}
```

#### 2. AIProcessingService Outbox

**Entity:** `JobOutbox.kt`
```kotlin
@Entity
@Table(name = "job_outbox")
class JobOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    var id: UUID? = null,
    
    @Column(name = "event_type", nullable = false)
    var event_type: String,  // "ANALYSIS_COMPLETED", "ANALYSIS_FAILED", etc.
    
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    var payload: Map<String, Any>,  // Enhanced metadata
    
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant = Instant.now()
)
```

**Transactional Write:** `JobStatusService.kt`
```kotlin
@Transactional
fun markJobAsCompleted(jobId: UUID, job: JobDTO, metadata: MetadataDTO) {
    // 1. Update job status
    val jobEntity = jobRepository.findById(jobId).get()
    jobEntity.status = jobStatus.COMPLETED
    jobEntity.payload = mapOf(
        "summary" to metadata.summary!!,
        "tags" to metadata.tags!!,
        "sensitivity" to metadata.sensitivity!!.name
    )
    jobRepository.save(jobEntity)
    
    // 2. Create outbox event (same transaction!)
    val outboxEvent = JobOutbox(
        event_type = "ANALYSIS_COMPLETED",
        payload = mapOf(
            "fileid" to job.fileId,
            "version" to job.version,
            "status" to jobStatus.COMPLETED.name,
            "metadata" to jobEntity.payload!!
        )
    )
    jobOutboxRepository.save(outboxEvent)
    // ✅ Both writes succeed or fail together
}
```

### Debezium CDC Configuration

**File Service Connector:**
```json
{
  "name": "fileservice-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "myuser",
    "database.password": "secret",
    "database.dbname": "filedb",
    "database.server.name": "tusService",
    "table.include.list": "public.files_outbox_events",
    "plugin.name": "pgoutput",
    "snapshot.mode": "initial",
    
    "transforms": "unwrap,router",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false",
    
    "transforms.router.type": "io.debezium.transforms.ByLogicalTableRouter",
    "transforms.router.topic.regex": "tusService.public.files_outbox_events",
    "transforms.router.topic.replacement": "file-outbox"
  }
}
```

**AI Service Connector:**
```json
{
  "name": "ai-job-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres_aireq",
    "database.port": "5432",
    "database.dbname": "aireqdb",
    "table.include.list": "public.job_outbox",
    "topic.prefix": "aiService",
    
    "transforms": "unwrap,router",
    "transforms.router.topic.replacement": "job-outbox"
  }
}
```

### Outbox Pattern Benefits

✅ **Atomic Operations**: DB writes + events are in same transaction  
✅ **No Lost Events**: Events are persisted before publishing  
✅ **Decoupling**: Services don't need direct Kafka dependency  
✅ **Reliability**: Debezium handles retries and failures  
✅ **Audit Trail**: Outbox tables provide event history  
✅ **Idempotency**: Duplicate events can be handled by consumers  

---

## 🛠️ Technology Stack

### Backend Services

| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 1.9.25 | Primary programming language |
| **Spring Boot** | 3.5.8/3.5.9 | Framework for microservices |
| **Spring AI** | 1.1.2 | AI integration framework |
| **Spring Security** | 6.2.x | OAuth2 resource server |
| **Spring Kafka** | 3.3.11 | Kafka integration |
| **Spring Data JPA** | - | Database ORM |
| **PostgreSQL** | 15 | Primary database (with logical replication) |
| **Flyway** | - | Database migrations |
| **Apache Kafka** | 4.1.0 | Event streaming platform |
| **Debezium** | 3.0.8 | Change Data Capture connector |
| **Apache PDFBox** | 2.0.31 | PDF text extraction |
| **MinIO** | Latest | S3-compatible object storage |

### Frontend

| Technology | Version | Purpose |
|------------|---------|---------|
| **React** | 18.x | UI framework |
| **TypeScript** | 5.x | Type-safe JavaScript |
| **Vite** | 5.x | Build tool |
| **React Bootstrap** | - | UI components |
| **tus-js-client** | 4.x | Resumable file uploads |

### Infrastructure

| Technology | Version | Purpose |
|------------|---------|---------|
| **Docker Compose** | - | Container orchestration |
| **Keycloak** | 26.4 | OAuth2/OIDC provider |
| **Kafka UI** | Latest | Kafka cluster monitoring |
| **Kafka Connect** | Latest | Debezium connectors |

### AI Providers

| Provider | Model | Use Case |
|----------|-------|----------|
| **Groq** | Llama 3.3 70B Versatile | PDF document analysis |
| **Groq** | Llama 3.1 8B Instant | Generic text file analysis |

---

## 🎯 Services Overview

### 1. TusUploaderService (Port 8081)

**Responsibilities:**
- Manages file uploads using TUS protocol (resumable uploads)
- Implements chunked upload with merge strategies (client-side/server-side)
- File versioning and metadata management
- Integration with MinIO for object storage
- Role-based access control (RBAC) for file operations
- Publisher of file upload events via Outbox pattern
- Consumer of AI analysis results

**Key Features:**
- ✅ TUS protocol support (creation, creation-with-upload, resumable, expiration)
- ✅ Automatic checksum verification (SHA-1, MD5)
- ✅ Content-Type auto-detection using Apache Tika
- ✅ File versioning system
- ✅ Sharing scopes: PERSONAL, DEPARTMENT, ORGANIZATION
- ✅ Scheduled cleanup of expired uploads (WALL-E task)

**Database Schema:**
- `uploads` - Upload session metadata
- `files` - File entities
- `file_versions` - Version history with metadata
- `files_outbox_events` - Outbox table for Kafka events
- `file_shares` - File sharing permissions

### 2. AIProcessingService (Port 8083)

**Responsibilities:**
- **Document Analysis**: AI-based metadata extraction (summary, tags, sensitivity)
- **Text Extraction**: Extract text from PDF and other formats
- **Semantic Chunking**: Split documents into meaningful chunks with overlap
- **Embedding Generation**: Convert text chunks to vector embeddings
- **Vector Storage**: Store embeddings in PostgreSQL pgvector
- **RAG Queries**: Natural language search and question-answering
- **Embedding Deletion**: Automatic cleanup when files are deleted
- **Job Management**: Track processing pipeline and handle failures

**Key Features:**
- ✅ Dual AI model strategy (PDF-specific Llama 3.3 70B vs generic Llama 3.1 8B)
- ✅ RAG pipeline with Ollama nomic-embed-text (768 dimensions)
- ✅ PostgreSQL pgvector with HNSW indexing for fast similarity search
- ✅ Event-driven embedding deletion via Kafka + Debezium CDC
- ✅ Semantic chunking with 500 tokens/chunk and 50 token overlap
- ✅ User isolation in vector search (filter by ownerId)
- ✅ PDF text extraction with Apache PDFBox
- ✅ Intelligent content truncation for token limits
- ✅ Retry mechanism with exponential backoff (5 attempts)
- ✅ Job status tracking (PENDING → COMPLETED/UNSUPPORTED/FAILED)
- ✅ Dead Letter Topic (DLT) for failed jobs

**Database Schema:**
- `jobs` - AI processing job records
- `job_outbox` - Outbox table for result events
- `vector_store` - pgvector table with HNSW index for embeddings
- `file_version_embeddings` - Tracking table linking chunks to vector store

**API Endpoints:**
- `POST /api/rag/query` - Query documents with natural language
- `GET /api/jobs/{id}/status` - Check processing job status

### 3. UserDetailService (Port 8082)

**Responsibilities:**
- User profile management
- Organization and department hierarchy
- User authentication support
- User search and listing

**Database Schema:**
- `users` - User profiles with organization/department

### 4. ApiGateway (Port 8080)

**Responsibilities:**
- Single entry point for all client requests
- OAuth2 authentication with Keycloak
- Token relay to backend services
- Request routing

**Routes:**
- `/api/v1/files/**` → TusUploaderService
- `/api/v1/uploads/**` → TusUploaderService
- `/api/v1/users/**` → UserDetailService
- `/ui/**` → Frontend

### 5. Frontend (Port 5173)

**Responsibilities:**
- File upload interface with progress tracking
- File browsing and management
- Resume/pause/cancel upload controls
- File metadata visualization
- User authentication (OAuth2 Authorization Code flow)

**Key Features:**
- ✅ TUS-based chunked uploads with progress bar
- ✅ File preview and download
- ✅ Metadata display (including AI-generated)
- ✅ Role-based UI (admin/manager/staff)
- ✅ Sharing scope selection

---

## 🧪 Testing Strategy

### Test Coverage

The project implements a **comprehensive testing strategy** with multiple test levels:

#### 1. Unit Tests

**Location:** `src/test/kotlin/.../unit/`

**Coverage:**
- Service layer logic (mocked dependencies)
- Business rules validation
- Data transformation
- Edge cases and error handling

**Example Test Categories:**
```text
// TusServiceTest.kt
- Upload creation
- Chunk appending
- Checksum verification
- Content-Type extraction from metadata
- Error scenarios

// FileServiceTest.kt
- File version creation
- Access control validation
- Metadata updates
```

**Tools:**
- JUnit 5
- MockK (Kotlin mocking library)
- SpringMockK (Spring + MockK integration)

#### 2. Integration Tests

**Location:** `src/test/kotlin/.../integration/`

**Coverage:**
- Service layer with real database
- Repository operations
- Transactional behavior
- Database constraints

**Example Test Categories:**
```text
// TusServiceTest (integration)
@DataJpaTest
- Upload creation with database persistence
- Content-Type extraction validation
- File versioning with cascade operations
- Upload completion flow
```

**Tools:**
- Spring Boot Test
- Testcontainers (PostgreSQL)
- H2 for some lightweight tests

#### 3. End-to-End (E2E) Tests

**Location:** `src/test/kotlin/.../e2e/`

**Coverage:**
- Complete API flows
- Controller → Service → Repository
- Authentication and authorization
- HTTP request/response validation

**Example Test Categories:**
```text
// UploadE2ETests.kt
- POST /uploads - Creation with upload
- PATCH /uploads/{id} - Chunk append
- HEAD /uploads/{id} - Upload info
- DELETE /uploads/{id} - Termination
- Error responses: 413 Payload Too Large, 404 Not Found, 403 Forbidden

// FileE2ETests.kt
- GET /files - List files with filters
- GET /files/{id}/download - Download with version
- Access control scenarios
```

**Tools:**
- @SpringBootTest
- RestTemplate
- TestRestTemplate
- Testcontainers (full stack)

#### 4. Test Configuration

**JaCoCo Code Coverage:**
```kotlin
tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}
```

**Test Profiles:**
```yaml
# src/test/resources/application.yml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    hibernate:
      ddl-auto: create-drop
```

### Running Tests

```bash
# Run all tests
./gradlew test

# Run tests with coverage report
./gradlew test jacocoTestReport

# Run only unit tests
./gradlew test --tests "*unit*"

# Run only integration tests
./gradlew test --tests "*integration*"

# Run only E2E tests
./gradlew test --tests "*e2e*"

# View coverage report
open build/reports/jacoco/test/html/index.html
```

### Test Metrics

| Service | Test Classes | Test Methods | Coverage Target |
|---------|--------------|--------------|-----------------|
| TusUploaderService | 12+ | 100+ | >80% |
| AIProcessingService | - | - | Manual testing |
| UserDetailService | 5+ | 40+ | >70% |

---

## ⚙️ Configuration & Setup

### Prerequisites

- Docker & Docker Compose
- Java 21 (JDK)
- Node.js 18+ (for frontend)
- Groq API Key (for AI features)

### Environment Variables

Create a `.env` file in the project root:

```bash
# Groq AI Configuration
GROQ_API_KEY=your-groq-api-key-here
GROQ_BASE_URL=https://api.groq.com/openai
GROQ_MODEL=llama-3.1-8b-instant

# AI Model Configuration
CHAT_MODEL=llama-3.1-8b-instant
PDF_MODEL=llama-3.3-70b-versatile

# Keycloak
KEYCLOAK_ADMIN_USER=administrator
KEYCLOAK_ADMIN_PASSWORD=admin
```

### Getting Groq API Key

1. Visit [https://console.groq.com/](https://console.groq.com/)
2. Sign up for a free account
3. Navigate to API Keys section
4. Create a new API key
5. Copy the key to your `.env` file

**Note:** Groq offers a generous free tier with rate limits suitable for development and testing.

### Installing Ollama (for RAG - Retrieval-Augmented Generation)
    - Download from: https://ollama.com/download
    - After installation, pull the required model:
      ```bash
      ollama pull nomic-embed-text:latest
      ```

### Docker Compose Setup

```bash
# Start infrastructure (Postgres, Kafka, Keycloak, MinIO)
docker-compose up -d

# Check services status
docker-compose ps

# View logs
docker-compose logs -f kafka
docker-compose logs -f kafka-connect
```

### Service Configuration

#### TusUploaderService (application.yml)

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/filedb
    username: myuser
    password: secret
  jpa:
    hibernate:
      ddl-auto: none
  flyway:
    enabled: true
    baseline-on-migrate: true

tus:
  version: 1.0.0
  extensions: creation,creation-defer-length,creation-with-upload,expiration,checksum,termination
  maxSize: 1073741824  # 1GB
  checksumAlgorithm: sha-1

minio:
  url: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  uploads-bucket: uploads
  files-bucket: files

kafka:
  bootstrap-servers: localhost:9094
  topic:
    file-upload: file-outbox

keycloak:
  client-id: tus-client
  client-secret: kBwAXCN3ljeGDubcnpSanUdUP19mcQG9
  token-uri: http://localhost:9090/realms/file-management/protocol/openid-connect/token
```

#### AIProcessingService (application.yml)

```yaml
server:
  port: 8083

spring:
  datasource:
    url: jdbc:postgresql://localhost:5435/aireqdb
    username: myuser
    password: secret
  
  ai:
    openai:
      api-key: ${GROQ_API_KEY}
      base-url: ${GROQ_BASE_URL}
      chat:
        options:
          model: ${GROQ_MODEL}

ai:
  chat:
    model: ${CHAT_MODEL:llama-3.1-8b-instant}
  pdf:
    model: ${PDF_MODEL:llama-3.3-70b-versatile}
  document:
    max-content-chars: 15000
    truncation-strategy: "beginning-end"
    beginning-percentage: 70

kafka:
  bootstrap-servers: "localhost:9094"
  consumer:
    auto-offset-reset: earliest
    group-id: "myId"

keycloak:
  client-id: ai-client
  client-secret: f9n2EePzamfcR3gieGYidhBjNjY3Jxr9
  token-uri: http://localhost:9090/realms/file-management/protocol/openid-connect/token
```

### Starting the Application

#### 1. Start Infrastructure

```bash
docker-compose up -d postgres postgres_aireq postgres_user minio keycloak kafka kafka-connect
```

Wait for services to be ready (~30 seconds).

#### 2. Setup Debezium Connectors

```bash
# File service outbox connector
curl -X POST http://localhost:8086/connectors \
  -H "Content-Type: application/json" \
  -d @AIProcessingService/debezium-file-outbox-connector.json

# AI service outbox connector
curl -X POST http://localhost:8086/connectors \
  -H "Content-Type: application/json" \
  -d @AIProcessingService/debezium-job-outbox-connector.json

# Verify connectors
curl http://localhost:8086/connectors
```

#### 3. Start Backend Services

```bash
# Terminal 1: TusUploaderService
cd TusUploaderService
./gradlew bootRun

# Terminal 2: AIProcessingService
cd AIProcessingService
./gradlew bootRun

# Terminal 3: UserDetailService
cd UserDetailService
./gradlew bootRun

# Terminal 4: ApiGateway
cd ApiGateway
./gradlew bootRun
```

#### 4. Start Frontend

```bash
cd fileportalfrontend
npm install
npm run dev
```

#### 5. Access the Application

- **Frontend:** http://localhost:5173
- **API Gateway:** http://localhost:8080
- **Keycloak:** http://localhost:9090
- **Kafka UI:** http://localhost:8084
- **MinIO Console:** http://localhost:9001

### Default Users (Keycloak)

| Username | Password | Role | Organization | Department |
|----------|----------|------|--------------|------------|
| admin    | admin | admin | Company1 | IT |
| manager1 | password | manager | Company1 | HR |
| riccardo | password | staff | Company1 | IT |

---

## ✨ Features Implemented

### Core Features

✅ **Resumable File Uploads** (TUS Protocol)
- Chunked uploads with progress tracking
- Resume interrupted uploads
- Pause/cancel controls
- Automatic checksum verification
- Upload expiration and cleanup

✅ **File Versioning**
- Automatic version tracking
- Download specific versions
- Version history visualization

✅ **AI-Powered Metadata**
- Automatic summary generation (2 sentences)
- Smart tag extraction (5 keywords)
- Sensitivity classification (4 levels)
- PDF and text file support
- Retry logic for reliability

✅ **Event-Driven Architecture**
- Transactional Outbox Pattern
- Debezium Change Data Capture
- Kafka-based async communication
- Dead Letter Topic for failures

✅ **Security & Access Control**
- OAuth2/OpenID Connect (Keycloak)
- Role-based access control (admin/manager/staff)
- Organization and department isolation
- Token-based authentication
- Secure file sharing

✅ **File Sharing**
- Three scopes: PERSONAL, DEPARTMENT, ORGANIZATION
- Permission management
- Share with specific users

### Optional Features

✅ **Transactional Outbox Pattern**
- Two-phase commit simulation
- Atomic writes + event publishing
- Debezium CDC integration
- Reliable event delivery

✅ **Dead Letter Topic (DLT)**
- Failed message handling
- Retry mechanism with backoff
- DLT routing for permanent failures
- Error logging and monitoring

✅ **Multi-Model AI Strategy**
- Model selection based on content type
- PDF-specific model (Llama 3.3 70B)
- Generic model (Llama 3.1 8B)
- Intelligent content truncation

✅ **RAG (Retrieval-Augmented Generation)**
- Semantic document search with pgvector
- Natural language query interface
- Vector embeddings with Ollama nomic-embed-text (768 dimensions)
- HNSW indexing for fast similarity search
- Context-aware answers using retrieved document chunks
- User isolation with ownerId filtering
- Automatic embedding deletion on file removal
- Source citations with similarity scores

✅ **Scheduled Tasks**
- WALL-E: Expired upload cleanup (hourly)
- Automatic merge of pending files
- Database maintenance

---

## ⚠️ Known Limitations

### 1. AI Model Limitations

**Token Limits:**
- Maximum content: ~15,000 characters (~3,750 tokens)
- Large files are truncated (beginning 70% + ending 30%)
- May lose context in middle sections of very large documents

**Rate Limiting:**
- Groq free tier has rate limits
- Production deployment should use paid tier or caching

### 2. Outbox Table Cleanup

**Issue:** Outbox tables (`files_outbox_events`, `job_outbox`) grow indefinitely

**Current State:** No automatic cleanup implemented

**Recommended Solution:**
```sql
-- Scheduled job to delete old events (>7 days)
DELETE FROM files_outbox_events 
WHERE created_at < NOW() - INTERVAL '7 days';

DELETE FROM job_outbox 
WHERE created_at < NOW() - INTERVAL '7 days';
```

**Future Enhancement:** Implement Spring @Scheduled task for automatic cleanup.

### 3. Dead Letter Topic (DLT) Handling

**Current Implementation:**
- Failed jobs (after retry) are sent to DLT
- No automatic reprocessing from DLT

**Missing Features:**
- DLT consumer for manual retry
- Admin UI for DLT message inspection
- Automatic reprocessing after cooling period

**Workaround:** Manual reprocessing via Kafka UI or custom scripts.

### 4. Testing Limitations

**AIProcessingService Testing:**
- No automated tests due to external AI dependency
- Manual testing only
- Mock AI responses not implemented

**Reason:** Groq API requires real API key and has rate limits

**Future Enhancement:** Mock AI responses for unit testing.

### 5. RAG System Limitations

**Asynchronous Processing:**
- Embeddings not available immediately after upload
- Processing time: 2-10 seconds depending on file size
- No real-time feedback to users

**Query Context Window:**
- Default TopK: 5 chunks (~2,500 tokens)
- May miss relevant context if spread across many chunks
- No automatic query expansion or reranking

**Embedding Deletion:**
- Asynchronous deletion via Kafka
- May take several seconds to complete
- No transactional guarantee with file deletion

**Language Support:**
- Primarily optimized for English (nomic-embed-text)
- Other languages may have reduced accuracy

**Future Enhancements:**
- Hybrid search (vector + keyword BM25)
- Cross-encoder reranking
- Multi-language embedding models
- Real-time embedding status tracking

---

## 📚 Additional Documentation

- **[Postman Collections](postman/)** - API testing collections
- **[Keycloak Configuration](keycloak/)** - Realm exports

---

## 👥 Team

**Gruppo 11** - Politecnico di Torino 
- Riccardo Mozzicato
- Iliyan Kostadinov
- Andrea Di Prima
- Samuele Scapellato

Web Applications II - A.Y. 2025/2026

---

## 📄 License

This project is part of an academic assignment for Politecnico di Torino.

---
