package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@Service
class DeadLetterTopicListener {

    private val log = LoggerFactory.getLogger(DeadLetterTopicListener::class.java)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(id = "dlt-consumer", topics = ["file-outbox-dlt"], autoStartup = "true")
    fun handleDeadLetterMessage(message: String) {
        log.error(" ========== MESSAGE IN DEAD LETTER TOPIC ==========")
        log.error("Message received in DLT after max retries")
        log.error("Message content: $message")

        try {
            // Try to parse and extract useful info
            val jsonNode = mapper.readTree(message)
            val fileId = jsonNode.get("fileId")?.asText()
            val version = jsonNode.get("version")?.asText()
            val filename = jsonNode.get("filename")?.asText()

            log.error("Job FAILED after maximum retries")
            log.error("File ID: $fileId")
            log.error("Filename: $filename")
            log.error("Version: $version")

            log.error("")
            log.error("⚠️ MANUAL INTERVENTION REQUIRED ⚠️")
            log.error("")
            log.error("Possible actions:")
            log.error("1. Check the jobs' table in the database for error details")
            log.error("2. Verify the file exists in MinIO storage")
            log.error("3. Review application logs for processing errors")
            log.error("4. Consider reprocessing the file manually")
            log.error("5. Notify the file owner if necessary")

            // TODO: Implement custom logic here
            // Examples:
            // - alertingService.sendAlert("Job failed in DLT", message)
            // - dltRepository.save(DltMessage(message, Instant.now()))
            // - emailService.notifyAdmin("Critical: Job in DLT", fileId)

        } catch (e: Exception) {
            log.error("Failed to parse DLT message: ${e.message}", e)
            log.error("Raw message: $message")
        }

        log.error("==================================================")
    }
}

