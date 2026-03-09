package g11.tusuploaderservice.dto

import java.util.UUID

data class FilesOutboxEventDTO(
    val jobid: UUID,
    val fileid: UUID,
    val version: Long,
    val type: String,
    val payload: Map<String, Any>
)

