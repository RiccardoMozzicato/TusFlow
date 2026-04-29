package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.FileStatus

data class MetadataAIDTO(
    val tags: String?,
    val summary: String?,
    val filename: String?,
    val metadata: String?,
    val contentType: String?,
    val ownerUserId: String?,
    val sensitivity: String?,
    val fileVersionId: String?,
    val analysisStatus: String?,
    val status: FileStatus
)
