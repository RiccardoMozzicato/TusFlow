package g11.tusuploaderservice.dto

import g11.tusuploaderservice.validator.LengthOrDeferConstraint
import java.util.UUID

@LengthOrDeferConstraint
data class FileDTO (
    val id: UUID,
    val fileName: String? = null,
    val createdAt: String,
    val completedAt: String? = null,
    val version: Long? = null,
    val size: Long? = null,
    val contentType: String? = null,
    val owner: UUID? = null,
    val ownerName: String? = null,
    val sharingScope: SharingScope? = null,
)

enum class SharingScope{
    PERSONAL,
    DEPARTMENT,
    ORGANIZATION
}
