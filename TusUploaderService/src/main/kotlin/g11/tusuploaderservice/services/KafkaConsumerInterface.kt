package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.FileUploadEvent

interface KafkaConsumerInterface {
    fun consumeFileUploadCompleted(message: String, receivedTimestamp: Long?)
}