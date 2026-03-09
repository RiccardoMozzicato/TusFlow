package g11.tusuploaderservice.dto

import java.io.ByteArrayInputStream

data class ChunkDTO (
    val filename: String?,
    val uuid: String,
    val number: Int,
    val inputStream: ByteArrayInputStream,
    val size: Long,
    val contentType: String?,
    val bucket: String,
    val version: Long? = null
)

