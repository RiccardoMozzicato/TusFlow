package g11.tusuploaderservice.dto

data class DownloadDTO(
    val filename: String?,
    val contentType: String?,
    val file: ByteArray
)
