package g11.tusuploaderservice.dto

import java.util.UUID

data class FileUploadEvent(
    val fileId: UUID,
    val fileVersionId: String,
    val version: Long,
    val ownerUserId: UUID,
    val filename: String,
    val contentType: String,
    val metadata: Map<String, String>?
)

