package g11.aiprocessingservice.dto

import java.time.Instant
import java.util.UUID

data class FileDTO(
    val id: UUID,
    val fileName: String,
    val version: Long,
    val contentType: String,
    val owner: UUID
)