package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
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
    /** Status of the current version: AVAILABLE when ready, MERGE_PENDING while still processing. */
    val status: FileStatus? = null,
    /** Caller's permission on this file; only set when listing files shared *with* the caller. */
    val permission: PERMISSION_LEVEL? = null,
    /** When the file was shared with the caller (RFC 9110 date); only set in the shared-with-me listing. */
    val sharedAt: String? = null,
)

enum class SharingScope{
    PERSONAL,
    DEPARTMENT,
    ORGANIZATION
}
