package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import g11.aiprocessingservice.metrics.ExtractionMetrics
import g11.aiprocessingservice.metrics.PipelineMetrics
import io.micrometer.tracing.Tracer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.io.Resource
import org.springframework.http.HttpStatus
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.retry.annotation.Backoff
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
    private val supportedContentTypes: SupportedContentTypes,
    private val deadLetterService: DeadLetterService,
    private val pipelineTracing: g11.aiprocessingservice.tracing.PipelineTracing,
    private val extractionMetrics: ExtractionMetrics,
    private val pipelineMetrics: PipelineMetrics,
) {
    private val log = LoggerFactory.getLogger(EmbeddingProcessor::class.java)

    // Non-blocking retry (see MetadataProcessor): on failure the record is parked on a retry topic
    // instead of sleeping this consumer thread, then sent to file-analyzed-dlt once attempts run out.
    // Re-processing is safe — downstream EmbeddingGenerator dedups chunks by (fileVersionId, index).
    @RetryableTopic(
        attempts = "\${ai.retry.embedding-attempts:3}",
        backoff = Backoff(delayExpression = "\${ai.retry.embedding-delay-ms:60000}"),
        kafkaTemplate = "kafkaTemplate",
        listenerContainerFactory = "embeddingKafkaListenerContainerFactory",
    )
    @KafkaListener(
        topics = ["file-analyzed"],
        groupId = "embedding-processor",
        containerFactory = "embeddingKafkaListenerContainerFactory",
    )
    fun processFileForEmbeddings(
        message: String,
        @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) receivedTimestamp: Long,
    ) {
        pipelineMetrics.recordEventLag("file-analyzed", receivedTimestamp)
        val event = objectMapper.readValue(message, FileAnalyzedEvent::class.java)

        log.info("Processing embeddings for file ${event.fileId} v${event.version}")

        // Skip types without extractable text (e.g. images). Defensive — the metadata stage only
        // publishes file-analyzed for analyzable types — but it also stops images from failing
        // extraction here and being parked on the DLT.
        if (!supportedContentTypes.isTextExtractable(event.contentType)) {
            log.info(
                "Skipping embeddings for file {} - no extractable text for content type: {}",
                event.fileId,
                event.contentType,
            )
            return
        }

        try {
            // 1. Download file
            val fileStream = pipelineMetrics.recordDownload("embedding") { downloadFile(event.fileId, event.version) }

            // 2. Text extraction (timed by base MIME type, e.g. application/pdf — drop any charset param)
            val baseMime = event.contentType.split(";").firstOrNull()?.trim() ?: event.contentType
            val extracted = extractionMetrics.recordExtraction(baseMime) {
                textExtractionService.extract(fileStream!!, event.contentType)
            }
            extractionMetrics.recordTextLength(extracted.content.length)

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
            extractionMetrics.recordChunks(chunks.size)

            // 5. Batch chunks (es. 50 per batch per parallelizzare)
            val batchSize = 5
            val batches = chunks.chunked(batchSize)
            extractionMetrics.countBatches(batches.size)

            pipelineTracing.tagEmbeddingPreparation(event.ownerUserId, event.fileId, event.contentType, chunks.size, batches.size)

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
                // Null key: spread this file's batches across partitions so EmbeddingGenerator
                // embeds them in parallel. Ordering is irrelevant — chunks dedupe by chunkIndex
                // and the fileVersionId travels inside the payload. Keying by fileVersionId would
                // pin every batch to one partition → one consumer thread → sequential embedding.
                kafkaTemplate.send(ProducerRecord(
                    "embeddings-batch", null,
                    null as String?,
                    objectMapper.writeValueAsString(batchEvent),
                    headers
                ))
            }

            log.info("Created ${batches.size} embedding batches for file ${event.fileId}")
            // No manual ack: the container commits per record on normal return (AckMode.RECORD).
        } catch (e: Exception) {
            log.error("Failed to process embeddings for file ${event.fileId}: ${e.message}", e)
            // Rethrow so @RetryableTopic routes the record to the retry topic / DLT.
            throw e
        }
    }

    /**
     * Drains the `file-analyzed` dead-letter topic into the audit table once embedding-preparation
     * retries are exhausted.
     */
    @DltHandler
    fun handleDlt(
        message: String,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_FQCN, required = false) exceptionType: ByteArray?,
        @Header(name = KafkaHeaders.DLT_EXCEPTION_MESSAGE, required = false) exceptionMessage: ByteArray?,
    ) {
        pipelineMetrics.countDeadLetter("file-analyzed")
        deadLetterService.record("file-analyzed", message, exceptionType, exceptionMessage)
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


