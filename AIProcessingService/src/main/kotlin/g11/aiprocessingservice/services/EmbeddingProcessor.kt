package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import io.micrometer.tracing.Tracer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.io.InputStream
import java.util.UUID


// EmbeddingProcessor.kt
@Service
class EmbeddingProcessor(
    private val textExtractionService: TextExtractionService,
    private val chunkingService: ChunkingService,
    private val kafkaTemplate: KafkaTemplate<String, String>,
    @Qualifier("TusUploaderService") private val fileServiceClient: RestClient,
    private val authService: AuthServiceInterface,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer,
) {
    private val log = LoggerFactory.getLogger(EmbeddingProcessor::class.java)

    @KafkaListener(topics = ["file-analyzed"], groupId = "embedding-processor")
    fun processFileForEmbeddings(message: String, acknowledgment: Acknowledgment) {
        val event = objectMapper.readValue(message, FileAnalyzedEvent::class.java)

        log.info("Processing embeddings for file ${event.fileId} v${event.version}")

        try {
            // 1. Download file
            val fileStream = downloadFile(event.fileId, event.version)

            // 2. Text extraction
            val extracted = textExtractionService.extract(fileStream!!, event.contentType)

            // 3. Build base metadata
            val baseMetadata = mapOf(
                "fileId" to event.fileId.toString(),
                "fileVersionId" to event.fileVersionId.toString(),
                "version" to event.version.toString(),
                "filename" to event.filename,
                "contentType" to event.contentType,
                "ownerUserId" to event.ownerUserId.toString(),

                // Metadata from AI analysis
                "summary" to (event.summary ?: ""),
                "tags" to (event.tags?.joinToString(",") ?: ""),
                "sensitivity" to (event.sensitivity ?: ""),

                // Extraction metadata
                "extractedAt" to extracted.extractedAt.toString(),
                "textLength" to extracted.content.length.toString()
            )

            // 4. Chunking
            val chunks = chunkingService.splitIntoChunks(
                extracted.content,
                event.fileVersionId,
                baseMetadata
            )

            // 5. Batch chunks (es. 50 per batch per parallelizzare)
            val batchSize = 5
            val batches = chunks.chunked(batchSize)

            val traceparentHeader: ByteArray? = tracer.currentSpan()?.context()?.let {
                "00-${it.traceId()}-${it.spanId()}-01".toByteArray(Charsets.UTF_8)
            }

            batches.forEachIndexed { batchIndex, batch ->
                val batchEvent = ChunkBatchEvent(
                    fileVersionId = event.fileVersionId,
                    batchNumber = batchIndex,
                    totalBatches = batches.size,
                    chunks = batch.map { chunk ->
                        chunk.copy(
                            metadata = chunk.metadata + mapOf(
                                "batchNumber" to batchIndex.toString(),
                                "totalBatches" to batches.size.toString()
                            )
                        )
                    }
                )

                val headers = RecordHeaders().apply {
                    traceparentHeader?.let { add("traceparent", it) }
                }
                kafkaTemplate.send(ProducerRecord(
                    "embeddings-batch", null,
                    event.fileVersionId.toString(),
                    objectMapper.writeValueAsString(batchEvent),
                    headers
                ))
            }

            log.info("Created ${batches.size} embedding batches for file ${event.fileId}")
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            log.error("Failed to process embeddings for file ${event.fileId}: ${e.message}", e)
            // TODO: Dead Letter Queue handling
            throw e
        }
    }

    fun downloadFile(fileid: UUID, version: Long?) : InputStream? {
        val token = authService.getAccessToken()

        try {
            val resource = fileServiceClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/files/${fileid}/download")
                        .queryParam("version", version)
                        .build()
                }
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .body(Resource::class.java)

            return resource?.inputStream
        } catch (ex: RestClientResponseException) {
            log.error("Error calling file service for download {}: {}", fileid, ex.message)
            if (ex.statusCode.value() == 404) {
                throw ResourceNotFoundException("Problem with file: $fileid")
            }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "File service error: ${ex.statusText}")
        }
    }

}


