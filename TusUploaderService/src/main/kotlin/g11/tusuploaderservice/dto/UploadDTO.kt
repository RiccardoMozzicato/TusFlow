package g11.tusuploaderservice.dto

import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.validator.LengthOrDeferConstraint
import io.minio.messages.Owner
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import java.time.Instant
import java.util.Date
import java.util.UUID

@LengthOrDeferConstraint
data class UploadDTO(
    val id: UUID? = null,
    val filename: String? = null,
    val fileUuid: UUID? = null,
    val checksum: String? = null,
    var uploadMetadata: Map<@Pattern(regexp = "\\A\\p{ASCII}+\\z", message = "Keys must be ASCII encoded")String, String>? = null,
    @field:Min(0L)
    val  uploadLength: Long? = null,
    @field:Min(0L)
    val uploadOffset: Long = 0,
    @field:Min(1L)
    @field:Max(1L)
    val uploadDeferLength: Int? = null,
    val contentType: String? = null,
    val uploadExpires: String? = null,
    val sharingScope: SharingScope? = null,
)

// This function is used only for the creation of a new Upload entity therefore we suppose that fileUuid is not null
fun UploadDTO.toEntity(param: additionalParameters): Upload {
    return Upload(
        fileId = this.fileUuid!!,
        filename = this.filename,
        contentType = param.contentType?: this.contentType,
        size = this.uploadLength,
        totalOffset = this.uploadOffset,
        metadata = this.uploadMetadata,
        checksum = this.checksum,
        status = param.status,
        createdAt = Date.from(Instant.now()),
        expiresAt = Date.from(Instant.parse(this.uploadExpires)),
        objectKey = param.objectKey,
        mergeMode = param.mergeMode,
        chunkCount = param.chunkCount,
        sharingScope = this.sharingScope ?: SharingScope.PERSONAL,
        organization = param.organization,
        department = param.department,
        ownerId = param.ownerId
    )
}

data class additionalParameters(
    val createdAt: String,
    val bucket: String,
    val objectKey: String?,
    val lastUpload: String?,
    val chunkCount: Int,
    val mergeMode: MergeMode,
    val status: UploadStatus,
    val contentType: String?=null,
    val organization: String,
    val department: String?,
    val ownerId: UUID
)