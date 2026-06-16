package g11.aiprocessingservice.services

interface KafkaInterface {
    fun onMessage(message: String)
}
