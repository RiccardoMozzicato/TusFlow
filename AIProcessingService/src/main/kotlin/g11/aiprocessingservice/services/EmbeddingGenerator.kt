package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
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
import org.springframework.transaction.annotation.Transactional
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
    @Transactional
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

        batch.chunks.forEach { chunk ->
            try {
                // 1. Verifica se questo chunk esiste già
                val existingEmbedding = embeddingRepository.findByFileVersionIdAndChunkIndex(
                    batch.fileVersionId,
                    chunk.chunkIndex
                )

                if (existingEmbedding != null) {
                    log.debug("Chunk {} for fileVersion {} already exists, skipping",
                        chunk.chunkIndex, batch.fileVersionId)
                    return@forEach  // Skip questo chunk
                }

                // 2. Genera UUID per il document
                val documentId = UUID.randomUUID()

                // 3. Crea Document per Spring AI con ID, content e metadata
                val document = Document(
                    documentId.toString(),
                    chunk.content,
                    chunk.metadata + mapOf(
                        "embeddingModel" to embeddingModelName,
                        "embeddingDimensions" to embeddingDimensions,
                        "processedAt" to Instant.now().toString()
                    )
                )

                // 4. Salva in vector_store (Spring AI gestisce embedding automaticamente)
                vectorStore.add(listOf(document))

                // 5. Salva tracking in tabella custom con il vector_store_id
                embeddingRepository.save(
                    FileVersionEmbedding(
                        fileVersionId = batch.fileVersionId,
                        chunkIndex = chunk.chunkIndex,
                        vectorStoreId = documentId,
                        contentPreview = chunk.content.take(200),
                        chunkTokens = chunk.metadata["chunkTokens"]?.toIntOrNull()
                    )
                )

                log.debug("Embedded chunk {} for fileVersion {} with vector_store_id {}",
                    chunk.chunkIndex, batch.fileVersionId, documentId)

            } catch (e: Exception) {
                log.error("Failed to embed chunk ${chunk.chunkIndex}: ${e.message}", e)
                // Continue con altri chunks invece di fallire tutto il batch
            }
        }

        log.info("Completed embedding batch ${batch.batchNumber} for fileVersion ${batch.fileVersionId}")
        // No manual ack: the container commits per record on normal return (AckMode.RECORD). If a
        // chunk threw above it was caught and logged, so the batch still completes and commits; only
        // an exception escaping this method triggers @RetryableTopic.
    }
}
