package g11.tusuploaderservice.services

interface KafkaConsumerInterface {
    fun consumeFileUploadCompleted(message: String)
}