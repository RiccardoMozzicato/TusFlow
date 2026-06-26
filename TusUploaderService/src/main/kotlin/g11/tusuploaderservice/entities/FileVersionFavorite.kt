package g11.tusuploaderservice.entities

import g11.tusuploaderservice.dto.FileVersionFavoriteDTO
import g11.tusuploaderservice.utils.RFC9110
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.springframework.data.jpa.repository.Temporal
import java.util.Date
import java.util.UUID

/**
 * A per-user favorite pinned to a specific file version.
 * The favorite stays attached to that version even when newer versions are uploaded.
 */
@Entity
@Table(name = "file_version_favorites")
open class FileVersionFavorite(

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    open var id: Long? = null,

    @Column(name = "user_id", nullable = false, updatable = false)
    open var userId: UUID,

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date,

    @ManyToOne(fetch = FetchType.EAGER) // File version data is always needed when reading favorites
    @JoinColumn(name = "file_version_id", nullable = false)
    open var fileVersion: FileVersion
)

fun FileVersionFavorite.toDTO(ownerName: String? = null): FileVersionFavoriteDTO {
    val file = requireNotNull(this.fileVersion.file) { "FileVersion must belong to a File" }
    return FileVersionFavoriteDTO(
        id = this.id,
        fileId = file.uuid,
        fileName = this.fileVersion.filename,
        version = this.fileVersion.version,
        size = this.fileVersion.size,
        contentType = this.fileVersion.contentType,
        status = this.fileVersion.status,
        ownerId = file.ownerId,
        ownerName = ownerName,
        favoritedAt = RFC9110(this.createdAt.toInstant())
    )
}
