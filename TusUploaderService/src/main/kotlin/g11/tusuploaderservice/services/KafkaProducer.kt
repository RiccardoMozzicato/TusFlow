package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.FileUploadEvent
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.security.access.prepost.PreAuthorize

@Service
class KafkaProducer(
    private val objectMapper: ObjectMapper
) : KafkaProducerInterface{

    @Autowired
    lateinit var kafkaTemplate: org.springframework.kafka.core.KafkaTemplate<String, String>

    private val log = LoggerFactory.getLogger(KafkaProducer::class.java)

    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun sendMessage(message: String) {
        log.debug("Sending message to Kafka: {}", message)
        val future = kafkaTemplate.sendDefault(message)
        future.whenComplete { result, ex ->
            if (ex == null) {
                val offset = result.recordMetadata.offset()
                log.debug(
                    "Sent message=[" + message +
                            "] with offset=[" + offset + "]"
                )
                log.info("Message sent successfully to Kafka with offset=[{}]", offset)
            } else {
                log.warn(
                    "Unable to send message=[" +
                            message + "] due to : " + ex.message
                )
                throw ex
            }
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun sendFileUploadMessage(fileUpload: FileUploadEvent) {
        log.debug("Processing FileUploadEvent for fileId: {}", fileUpload.fileId)
        try {
            log.debug("Serializing FileUploadEvent: fileId={}", fileUpload.fileId)
            sendMessage(objectMapper.writeValueAsString(fileUpload))
            log.info("FileUploadEvent sent successfully to Kafka: fileId={}", fileUpload.fileId)
        }
        catch (e: com.fasterxml.jackson.core.JsonProcessingException){
            log.error("Failed to serialize FileUploadEvent: fileId={}, error={}", fileUpload.fileId, e.message)
        }
        catch (e: Exception) {
            log.error("Unable to send FileUploadEvent message to Kafka for fileId:{}, details:{}", fileUpload.fileId, e.message)
        }
    }
}