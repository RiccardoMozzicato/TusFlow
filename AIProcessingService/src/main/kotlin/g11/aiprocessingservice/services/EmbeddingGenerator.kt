package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
import g11.aiprocessingservice.dto.ChunkData
import g11.aiprocessingservice.entities.FileVersionEmbedding
import g11.aiprocessingservice.repositories.FileVersionEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

// EmbeddingGenerator.kt
@Service
class EmbeddingGenerator(
    private val vectorStore: VectorStore,
    private val embeddingRepository: FileVersionEmbeddingRepository,
    private val objectMapper: ObjectMapper,
    private val deadLetterService: DeadLetterService,
    private val pipelineTracing: g11.aiprocessingservice.tracing.PipelineTracing,
    @Value("\${app.embedding.model:qwen3-embedding-4b}") private val embeddingModelName: String,
    @Value("\${app.embedding.dimensions:2560}") private val embeddingDimensions: String,
) {
    private val log = LoggerFactory.getLogger(EmbeddingGenerator::class.java)

    /**
     * Drains the `embeddings-batch` dead-letter topic into the audit table once embedding-generation
     * retries are exhausted.
     */
    @DltHandler
    fun handleDlt(
        message: String,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) exceptionType: ByteArray?,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) exceptionMessage: ByteArray?,
    ) {
        deadLetterService.record("embeddings-batch", message, exceptionType, exceptionMessage)
    }

    // Non-blocking retry (see MetadataProcessor): a failed batch is parked on a retry topic rather
    // than sleeping this consumer thread, then sent to embeddings-batch-dlt once attempts run out.
    // Re-processing is safe — chunks are deduped by (fileVersionId, chunkIndex) before embedding.
    @RetryableTopic(
        attempts = "\${ai.retry.embedding-attempts:3}",
        backoff = Backoff(delayExpression = "\${ai.retry.embedding-delay-ms:60000}"),
        kafkaTemplate = "kafkaTemplate",
        listenerContainerFactory = "embeddingKafkaListenerContainerFactory",
    )
    @KafkaListener(
        topics = ["embeddings-batch"],
        groupId = "embedding-generator",
        containerFactory = "embeddingKafkaListenerContainerFactory",
        // Consumer threads embedding batches in parallel (each = 1 concurrent call to the model
        // backend). Configurable so a single self-hosted GPU can be throttled to 1-2.
        concurrency = "\${app.embedding.concurrency:3}"
    )
    // Deliberately NOT @Transactional: each chunk is persisted independently (per-call repository
    // transaction + vectorStore.add), so when this method throws to trigger a retry, the chunks that
    // did succeed on this pass stay committed and are skipped (deduped) on the next backoff
    // redelivery instead of being rolled back and re-embedded.
    fun generateEmbeddings(message: String) {
        val batch = objectMapper.readValue(message, ChunkBatchEvent::class.java)

        log.info("Generating embeddings for batch ${batch.batchNumber}/${batch.totalBatches} " +
                "of fileVersion ${batch.fileVersionId}")

        // The batch event carries no owner field, but every chunk's metadata does (set in
        // EmbeddingProcessor's baseMetadata) — read it so the embedding observation groups by user.
        val ownerUserId = batch.chunks.firstOrNull()?.metadata?.get("ownerUserId")
        pipelineTracing.tagEmbeddingBatch(
            ownerUserId, batch.fileVersionId, batch.batchNumber, batch.totalBatches,
            batch.chunks.size, embeddingModelName, embeddingDimensions,
        )

        // Embed each chunk independently so one bad chunk doesn't abort the others; collect failures.
        var embedded = 0
        var skipped = 0
        var failed = 0
        var lastFailure: Exception? = null

        batch.chunks.forEach { chunk ->
            try {
                if (embedChunk(batch.fileVersionId, chunk)) embedded++ else skipped++
            } catch (e: Exception) {
                failed++
                lastFailure = e
                log.error("Failed to embed chunk ${chunk.chunkIndex}: ${e.message}", e)
                // Continue con altri chunks invece di fallire tutto il batch
            }
        }

        // If any chunk failed, rethrow so @RetryableTopic redelivers the whole batch with backoff and,
        // once attempts are exhausted, routes it to embeddings-batch-dlt. On redelivery the chunks
        // already embedded on this pass are skipped via the (fileVersionId, chunkIndex) dedup lookup,
        // so only the still-failing chunks are actually retried.
        if (failed > 0) {
            throw IllegalStateException(
                "$failed of ${batch.chunks.size} chunk(s) of batch ${batch.batchNumber}/" +
                    "${batch.totalBatches} for fileVersion ${batch.fileVersionId} failed to embed " +
                    "($embedded embedded / $skipped already present); triggering retry",
                lastFailure,
            )
        }

        log.info("Completed embedding batch ${batch.batchNumber} for fileVersion " +
            "${batch.fileVersionId} ($embedded embedded / $skipped skipped)")
        // No manual ack: the container commits per record on normal return (AckMode.RECORD).
    }

    /**
     * Embeds a single chunk. Returns true when it was newly embedded, false when it was skipped
     * because it already exists (deduped by fileVersionId + chunkIndex). Throws if the embedding
     * backend call fails — callers decide whether to tolerate, re-queue, or rethrow.
     */
    private fun embedChunk(fileVersionId: Long, chunk: ChunkData): Boolean {
        val existingEmbedding = embeddingRepository.findByFileVersionIdAndChunkIndex(
            fileVersionId,
            chunk.chunkIndex,
        )
        if (existingEmbedding != null) {
            log.debug("Chunk {} for fileVersion {} already exists, skipping",
                chunk.chunkIndex, fileVersionId)
            return false  // already embedded in a previous run
        }

        val documentId = UUID.randomUUID()
        val document = Document(
            documentId.toString(),
            chunk.content,
            chunk.metadata + mapOf(
                "embeddingModel" to embeddingModelName,
                "embeddingDimensions" to embeddingDimensions,
                "processedAt" to Instant.now().toString(),
            ),
        )

        // Spring AI generates the embedding and writes vector_store inside this call.
        vectorStore.add(listOf(document))

        embeddingRepository.save(
            FileVersionEmbedding(
                fileVersionId = fileVersionId,
                chunkIndex = chunk.chunkIndex,
                vectorStoreId = documentId,
                contentPreview = chunk.content.take(200),
                chunkTokens = chunk.metadata["chunkTokens"]?.toIntOrNull(),
            ),
        )

        log.debug("Embedded chunk {} for fileVersion {} with vector_store_id {}",
            chunk.chunkIndex, fileVersionId, documentId)
        return true
    }
}
