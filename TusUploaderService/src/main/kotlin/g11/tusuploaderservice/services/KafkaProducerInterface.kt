package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.FileUploadEvent

interface KafkaProducerInterface {
    fun sendMessage(message: String)
    fun sendFileUploadMessage(fileUpload: FileUploadEvent)
}