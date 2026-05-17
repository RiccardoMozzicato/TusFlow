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
        val future = kafkaTemplate.sendDefault(message)
        future.whenComplete { result, ex ->
            if (ex == null) {
                log.debug(
                    "Sent message=[" + message +
                            "] with offset=[" + result.recordMetadata.offset() + "]"
                )
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
        try {
            sendMessage(objectMapper.writeValueAsString(fileUpload))
        }
        catch (e: com.fasterxml.jackson.core.JsonProcessingException){
            log.error("Failed to serialize FileUploadEvent: {}", e.message)
        }
        catch (e: Exception) {
            log.error("Unable to send FileUploadEvent message to Kafka for fileId:{}, details:{}", fileUpload.fileId, e.message)
        }
    }
}