package g11.aiprocessingservice.services

import org.springframework.kafka.support.Acknowledgment

interface KafkaInterface {
    fun onMessage(message: String, acknowledgment: Acknowledgment)
}