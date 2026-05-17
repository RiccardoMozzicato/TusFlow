package g11.tusuploaderservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.tusuploaderservice.dto.MetadataAIDTO
import g11.tusuploaderservice.entities.FileStatus
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.util.*

@Service
class KafkaConsumer(
    private val fileService: FileServiceInterface,
    private val metadataSSEService: MetadataSSEService,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer,
    private val propagator: Propagator,
): KafkaConsumerInterface {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["job-outbox"],
        groupId = "tus-uploader-service"
    )
    override fun consumeFileUploadCompleted(message: String) {
        log.info("Received Kafka message: {}", message)

        // Resume the upstream trace carried in the Debezium outbox payload body.
        val traceparent = extractTraceparentFromPayload(message)
        val span = if (traceparent != null) {
            propagator.extract(mapOf("traceparent" to traceparent)) { map, key -> map[key] }
                .name("kafka receive job-outbox")
                .start()
        } else {
            tracer.nextSpan().name("kafka receive job-outbox").start()
        }
        val scope = tracer.withSpan(span)
        try {
            // Parse the message
            val jsonNode = objectMapper.readTree(message)

            // Check event type
            val eventType = jsonNode.get("event_type")?.asText()

            // Only process terminal AI analysis events.
            if (eventType !in setOf("ANALYSIS_COMPLETED", "ANALYSIS_UNSUPPORTED", "ANALYSIS_FAILED")) {
                log.debug("Ignoring event type: {}", eventType)
                return
            }

            // Parse the payload field which contains the actual event data
            val payloadString = jsonNode.get("payload").asText()
            val payloadNode = objectMapper.readTree(payloadString)

            // Extract fileId and version from the payload (not from the root)
            val fileId = UUID.fromString(payloadNode.get("fileid").asText())
            val version = payloadNode.get("version").asLong()

            // Extract metadata from the payload
            val metadataNode = payloadNode.get("metadata")
            val metadata = mutableMapOf<String, String>()

            if (metadataNode != null && metadataNode.isObject) {
                val fieldNames = metadataNode.fieldNames()
                while (fieldNames.hasNext()) {
                    val key = fieldNames.next()
                    val value = metadataNode.get(key)
                    // Handle nested objects by converting to JSON string
                    metadata[key] = if (value.isObject || value.isArray) {
                        objectMapper.writeValueAsString(value)
                    } else {
                        value.asText()
                    }
                }
            }

            log.info("Processing {} event for fileId: {}, version: {}, metadata: {}",
                eventType, fileId, version, metadata)

            when (eventType) {
                "ANALYSIS_COMPLETED" -> {
                    // Update file metadata using internal method (bypasses auth checks)
                    fileService.updateFileMetadata(fileId, version, metadata)
                    log.info("Successfully updated metadata for file: {} with event type: {}", fileId, eventType)
                    metadataSSEService.notifyAnalysisCompleted(
                        fileId,
                        version,
                        metadata.toMetadataAIDTO()
                    )
                }
                "ANALYSIS_UNSUPPORTED" -> {
                    fileService.updateFileMetadata(fileId, version, metadata)
                    log.info("Successfully updated metadata for file: {} with event type: {}", fileId, eventType)
                    metadataSSEService.notifyAnalysisUnsupported(
                        fileId,
                        version,
                        payloadNode.get("reason")?.asText()
                    )
                }
                "ANALYSIS_FAILED" -> {
                    val failedMetadata = metadata.withFailureStatus(payloadNode.get("error")?.asText())
                    fileService.updateFileMetadata(fileId, version, failedMetadata)
                    log.info("AI analysis failed for file: {} version: {}. Persisted failure metadata and notifying SSE clients.", fileId, version)
                    metadataSSEService.notifyAnalysisFailed(
                        fileId,
                        version,
                        payloadNode.get("error")?.asText()
                    )
                }
            }
        } catch (e: Exception) {
            log.error("Error processing Kafka message: {}", message, e)
            throw e
        } finally {
            scope.close()
            span.end()
        }
    }

    /**
     * Extracts the W3C traceparent from the Debezium outbox message body.
     * Debezium wraps the DB payload as an escaped JSON string in the "payload" field.
     */
    private fun extractTraceparentFromPayload(message: String): String? = try {
        val root = objectMapper.readTree(message)
        root["payload"]?.asText()?.let { payloadStr ->
            objectMapper.readTree(payloadStr)["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
        } ?: root["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    private fun Map<String, String>.toMetadataAIDTO(): MetadataAIDTO {
        return MetadataAIDTO(
            tags = this["tags"],
            summary = this["summary"],
            filename = this["filename"],
            contentType = this["contentType"],
            ownerUserId = this["ownerUserId"],
            sensitivity = this["sensitivity"],
            fileVersionId = this["fileVersionId"],
            analysisStatus = this["analysisStatus"],
            status = FileStatus.AVAILABLE
        )
    }

    private fun Map<String, String>.withFailureStatus(error: String?): Map<String, String> {
        val failureDetails = mapOf("error" to (error ?: "AI analysis failed"))
        return this.toMutableMap().apply {
            put("summary", "AI analysis failed")
            put("tags", "failed")
            put("analysisStatus", "FAILED")
            put("metadata", objectMapper.writeValueAsString(failureDetails))
        }
    }
}
