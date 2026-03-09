package g11.tusuploaderservice.entities

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.utils.RFC9110
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.Temporal
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class UploadStatus {
    PENDING,
    UPLOADING,
    MERGE_PENDING,
    DELETED
}

enum class MergeMode {
    SERVER_SIDE,
    CLIENT_SIDE
}

@Entity
@Table(name = "uploads")
open class Upload(

    @Id
    @GeneratedValue(generator = "uuid2")
    open var id: UUID? = null,

    @Column(nullable = false)
    open var fileId: UUID ,

    @Column(nullable = false)
    open var ownerId: UUID,

    @Column(nullable = true)
    open var filename: String?,

    @Column(nullable = true)
    open var contentType: String?,

    @Column
    open var size: Long?,

    @Column
    open var totalOffset: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var metadata: Map<String, String>?,

    @Column
    open var checksum: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: UploadStatus,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var expiresAt: Date,

    @Column
    open var objectKey: String?,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var mergeMode: MergeMode,

    @Column(nullable = false)
    open var chunkCount: Int = 0,

    @Column
    open var fileVersionId: Long? = null,

    @Column(nullable = false)
    open var sharingScope: SharingScope = SharingScope.PERSONAL,

    @Column(nullable = false)
    open var organization: String,

    @Column(nullable = true)
    open var department: String?
) {

    override fun toString(): String {
        return "Upload(id=$id, fileId=$fileId, ownerId=$ownerId, filename=$filename, contentType=$contentType, size=$size, totalOffset=$totalOffset, metadata=$metadata, checksum=$checksum, status=$status, createdAt=$createdAt, expiresAt=$expiresAt, objectKey=$objectKey, mergeMode=$mergeMode, chunkCount=$chunkCount, fileVersionId=$fileVersionId, sharingScope=$sharingScope, organization=$organization, department=$department)"
    }
}

fun Upload.toDTO() = UploadDTO(
    id = this.id,
    fileUuid = this.fileId,
    filename = this.filename,
    checksum = this.checksum,
    uploadMetadata = this.metadata,
    uploadLength = this.size,
    uploadOffset = this.totalOffset,
    uploadDeferLength = if(this.size != null) null else 1,
    uploadExpires = RFC9110(this.expiresAt.toInstant()),
    sharingScope = this.sharingScope
)


private fun RFC9110(instant: Instant): String {
    val formatter = DateTimeFormatter
        .ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
        .withZone(ZoneOffset.UTC)
    return formatter.format(instant)
}