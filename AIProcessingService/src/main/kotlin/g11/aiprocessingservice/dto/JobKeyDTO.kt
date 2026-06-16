package g11.aiprocessingservice.dto

import java.util.UUID

/** A (fileId, version) pair identifying the AI job to look up in a batch request. */
data class JobKeyDTO(
    val fileId: UUID,
    val version: Long,
)
