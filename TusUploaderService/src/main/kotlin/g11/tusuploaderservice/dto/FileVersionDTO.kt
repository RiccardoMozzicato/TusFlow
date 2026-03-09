package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import java.util.*

data class FileVersionDTO(
    val id: Long?,
    val version: Long,
    val filename: String,
    val size: Long,
    val contentType: String,
    val checksum: String?,
    val status: FileStatus,
    val metadata: Map<String, String>?,
    val createdAt: Date,
    val lastUpdateAt: Date
)

fun FileVersion.toDTO() = FileVersionDTO(
    id = this.id,
    version = this.version,
    filename = this.filename,
    size = this.size,
    contentType = this.contentType,
    checksum = this.checksum,
    status = this.status,
    metadata = this.metadata,
    createdAt = this.createdAt,
    lastUpdateAt = this.lastUpdateAt
)

