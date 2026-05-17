package g11.aiprocessingservice.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class FileDeletionListener(
    private val embeddingDeletionService: EmbeddingDeletionService
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(
        id = "file-deletion-consumer",
        topics = ["file-outbox"],
        groupId = "ai-service-file-deletion"
    )
    fun handleFileDeleted(message: String, acknowledgment: Acknowledgment) {
        try {
            log.info("FileDeletionListener received message: $message")

            // Parse il messaggio Debezium
            val kafkaEvent = mapper.readTree(message)

            // Estrai event_type (dovrebbe essere al primo livello dopo ExtractNewRecordState)
            val eventType = kafkaEvent.get("event_type")?.asText()

            log.info("Event type: $eventType")

            // Filtra solo eventi FILE_DELETED
            if (eventType != "FILE_DELETED") {
                log.debug("Ignoring event type: $eventType")
                acknowledgment.acknowledge()
                return
            }

            log.info("Processing FILE_DELETED event")

            // Il campo payload può essere una stringa JSON o un oggetto diretto
            val payloadNode = kafkaEvent.get("payload")

            if (payloadNode == null || payloadNode.isNull) {
                log.error("Payload is null or missing")
                acknowledgment.acknowledge()
                return
            }

            // Parse il payload
            @Suppress("UNCHECKED_CAST")
            val payloadData: Map<String, Any> = if (payloadNode.isTextual) {
                // Se è una stringa, parsala come JSON
                mapper.readValue(payloadNode.asText(), Map::class.java) as Map<String, Any>
            } else {
                // Se è già un oggetto, convertilo direttamente
                mapper.convertValue(payloadNode, Map::class.java) as Map<String, Any>
            }

            log.info("Payload data: $payloadData")

            // Estrai i dati dal payload
            val fileId = payloadData["fileId"]?.toString() ?: run {
                log.error("fileId is missing from payload")
                acknowledgment.acknowledge()
                return
            }

            // Estrai fileVersionIds come lista
            @Suppress("UNCHECKED_CAST")
            val fileVersionIdsRaw = payloadData["fileVersionIds"] as? List<*> ?: run {
                log.error("fileVersionIds is missing or invalid in payload. Payload keys: ${payloadData.keys}")
                acknowledgment.acknowledge()
                return
            }

            // Converte gli ID delle versioni in Long
            val fileVersionIds = fileVersionIdsRaw.mapNotNull { versionId ->
                when (versionId) {
                    is Number -> versionId.toLong()
                    is String -> try {
                        versionId.toLong()
                    } catch (e: NumberFormatException) {
                        log.warn("Cannot convert fileVersionId '$versionId' to Long: ${e.message}")
                        null
                    }
                    else -> {
                        log.warn("Unexpected type for fileVersionId: ${versionId?.javaClass?.name}")
                        null
                    }
                }
            }

            if (fileVersionIds.isEmpty()) {
                log.warn("No valid fileVersionIds found in payload for fileId: $fileId")
                acknowledgment.acknowledge()
                return
            }

            log.info("Processing file deletion for fileId: $fileId with ${fileVersionIds.size} versions: $fileVersionIds")

            // Cancella gli embeddings per ogni versione del file
            var deletedVersions = 0
            var errorCount = 0

            fileVersionIds.forEach { fileVersionId ->
                try {
                    log.info("Deleting embeddings for fileVersionId: $fileVersionId")
                    embeddingDeletionService.deleteEmbeddingsByFileVersionId(fileVersionId)
                    deletedVersions++
                    log.info("Successfully deleted embeddings for fileVersionId: $fileVersionId")
                } catch (e: Exception) {
                    errorCount++
                    log.error("Error deleting embeddings for fileVersionId $fileVersionId: ${e.message}", e)
                    // Continua con le altre versioni anche se una fallisce
                }
            }

            log.info("Completed processing file deletion for fileId: $fileId - " +
                    "deleted $deletedVersions versions, $errorCount errors")

            // ACK solo se abbiamo processato almeno una versione con successo
            if (deletedVersions > 0 || fileVersionIds.isEmpty()) {
                acknowledgment.acknowledge()
                log.info("Acknowledged FILE_DELETED event for fileId: $fileId")
            } else {
                throw RuntimeException("Failed to delete embeddings for all versions of fileId: $fileId")
            }

        } catch (e: Exception) {
            log.error("Error processing file deletion event: ${e.message}", e)
            // Non fare acknowledge in caso di errore, il messaggio verrà riprocessato
            throw e
        }
    }
}

