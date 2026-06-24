package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.UploadStatus
import java.util.UUID

/**
 * A resumable in-flight upload belonging to the current user, returned by
 * GET /api/v1/files/my-uploads. The client uses this list to decide which of its
 * locally-persisted uploads (held in IndexedDB) it may resume after a reload.
 *
 * Deliberately carries NO offset: the tus HEAD on /api/v1/uploads/{fileId} remains
 * the single source of truth for how many bytes the server already has.
 */
data class PendingUploadDTO(
    val fileId: UUID,
    val filename: String?,
    val contentType: String?,
    val size: Long?,
    val status: UploadStatus,
    /** RFC-9110 timestamp of when the upload was created. */
    val createdAt: String,
)
