package g11.tusuploaderservice.dto

import java.util.UUID

data class FileDeletedEvent(
    val fileId: UUID,
    val deletedAt: String,
    val deletedBy: UUID?,
    val fileVersionIds: List<Long>
)

