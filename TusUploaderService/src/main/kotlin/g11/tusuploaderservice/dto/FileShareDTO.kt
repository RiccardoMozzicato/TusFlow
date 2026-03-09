package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import java.util.UUID

data class FileShareDTO (
    val id: Long?,
    val fileID: UUID,
    val userID: UUID,
    val permission: PERMISSION_LEVEL,
    val timestamp: String
)