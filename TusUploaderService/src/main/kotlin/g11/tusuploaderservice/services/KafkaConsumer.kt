package g11.tusuploaderservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service
import java.util.*

@Service
class KafkaConsumer(
    private val fileService: FileServiceInterface,
    private val objectMapper: ObjectMapper
): KafkaConsumerInterface {
    private val log = LoggerFactory.getLogger(javaClass)

    @KafkaListener(
        topics = ["job-outbox"],
        groupId = "tus-uploader-service"
    )
    override fun consumeFileUploadCompleted(message: String) {
        log.info("Received Kafka message: {}", message)

        try {
            // Parse the message
            val jsonNode = objectMapper.readTree(message)

            // Check event type
            val eventType = jsonNode.get("event_type")?.asText()

            // Only process ANALYSIS_COMPLETED and ANALYSIS_UNSUPPORTED events
            if (eventType != "ANALYSIS_COMPLETED" && eventType != "ANALYSIS_UNSUPPORTED") {
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

            // Update file metadata using internal method (bypasses auth checks)
            fileService.updateFileMetadata(fileId, version, metadata)

            log.info("Successfully updated metadata for file: {} with event type: {}", fileId, eventType)
        } catch (e: Exception) {
            log.error("Error processing Kafka message: {}", message, e)
            throw e
        }
    }
}