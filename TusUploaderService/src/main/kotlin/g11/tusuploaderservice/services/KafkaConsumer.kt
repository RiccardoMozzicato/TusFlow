package g11.tusuploaderservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.tusuploaderservice.metrics.TusMetrics
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service
import java.util.*

@Service
class KafkaConsumer(
    private val fileService: FileServiceInterface,
    private val objectMapper: ObjectMapper,
    private val tracer: Tracer,
    private val propagator: Propagator,
    private val metrics: TusMetrics,
): KafkaConsumerInterface {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["job-outbox"],
        groupId = "tus-uploader-service"
    )
    override fun consumeFileUploadCompleted(
        message: String,
        @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) receivedTimestamp: Long,
    ) {
        log.debug("Received Kafka message: {}", message)
        metrics.recordEventLag("job-outbox", receivedTimestamp)

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
            if (eventType !in setOf("ANALYSIS_COMPLETED", "ANALYSIS_UNSUPPORTED", "ANALYSIS_FAILED", "ANALYSIS_DEAD_LETTERED")) {
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

            log.debug("Processing {} event for fileId: {}, version: {}, metadata: {}",
                eventType, fileId, version, metadata)

            when (eventType) {
                "ANALYSIS_COMPLETED" -> {
                    // Update file metadata using internal method (bypasses auth checks)
                    fileService.updateFileMetadata(fileId, version, metadata)
                    log.info("Successfully updated metadata for file: {} with event type: {}", fileId, eventType)
                }
                "ANALYSIS_UNSUPPORTED" -> {
                    fileService.updateFileMetadata(fileId, version, metadata)
                    log.info("Successfully updated metadata for file: {} with event type: {}", fileId, eventType)
                }
                "ANALYSIS_FAILED" -> {
                    val failedMetadata = metadata.withFailureStatus()
                    fileService.updateFileMetadata(fileId, version, failedMetadata)
                    log.info("AI analysis failed for file: {} version: {}. Persisted failure metadata.", fileId, version)
                }
                "ANALYSIS_DEAD_LETTERED" -> {
                    // Terminal: every automatic and manual retry was exhausted. Persist the failure
                    // on the file (same as a normal failure). The live notification to the browser is
                    // pushed directly by AIProcessingService over STOMP, not from here.
                    val failedMetadata = metadata.withFailureStatus()
                    fileService.updateFileMetadata(fileId, version, failedMetadata)
                    log.warn("AI analysis dead-lettered for file: {} version: {}. Retries exhausted.", fileId, version)
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

    private fun extractTraceparentFromPayload(message: String): String? = try {
        val root = objectMapper.readTree(message)
        root["payload"]?.asText()?.let { payloadStr ->
            objectMapper.readTree(payloadStr)["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
        } ?: root["traceparent"]?.asText()?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }


    private fun Map<String, String>.withFailureStatus(): Map<String, String> {
        return this.toMutableMap().apply {
            put("analysisStatus", "FAILED")
        }
    }
}
