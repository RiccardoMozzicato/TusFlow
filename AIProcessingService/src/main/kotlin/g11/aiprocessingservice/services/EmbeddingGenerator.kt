package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
import g11.aiprocessingservice.entities.FileVersionEmbedding
import g11.aiprocessingservice.repositories.FileVersionEmbeddingRepository
import org.slf4j.LoggerFactory
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

// EmbeddingGenerator.kt
@Service
class EmbeddingGenerator(
    private val vectorStore: VectorStore,
    private val embeddingRepository: FileVersionEmbeddingRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(EmbeddingGenerator::class.java)

    @KafkaListener(
        topics = ["embeddings-batch"],
        groupId = "embedding-generator",
        concurrency = "3"  // 3 consumer in parallelo
    )
    @Transactional
    fun generateEmbeddings(message: String, acknowledgment: Acknowledgment) {
        val batch = objectMapper.readValue(message, ChunkBatchEvent::class.java)

        log.info("Generating embeddings for batch ${batch.batchNumber}/${batch.totalBatches} " +
                "of fileVersion ${batch.fileVersionId}")

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
                        "embeddingModel" to "nomic-embed-text",
                        "embeddingDimensions" to "768",
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

        //ACK manuale SOLO dopo processing completo con successo
        acknowledgment.acknowledge()
    }
}
