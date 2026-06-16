package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.FileStatus
import java.util.UUID

/** A user's favorite pinned to a specific file version, enriched for direct rendering. */
data class FileVersionFavoriteDTO(
    val id: Long?,
    val fileId: UUID,
    /** Filename of the favorited version (may differ from the file's latest filename). */
    val fileName: String,
    val version: Long,
    val size: Long?,
    val contentType: String?,
    val status: FileStatus,
    val ownerId: UUID,
    /** Owner's display name, resolved from the user service; null if unavailable. */
    val ownerName: String? = null,
    /** When the user favorited this version (RFC 9110 date). */
    val favoritedAt: String
)
