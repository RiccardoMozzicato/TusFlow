package g11.tusuploaderservice.entities

import com.fasterxml.jackson.annotation.JsonManagedReference
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.SharingScope
import jakarta.persistence.CascadeType
import g11.tusuploaderservice.utils.RFC9110
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.boot.model.internal.DelayedParameterizedTypeBean
import org.springframework.data.jpa.repository.Temporal
import java.util.Date
import java.util.UUID


@Entity
@Table(name = "files")
open class File(

    @Id
    @Column(updatable = false, nullable = false, unique = true)
    open var uuid: UUID,

    @Column(nullable = false)
    open var ownerId: UUID,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var lastUpdateAt: Date,

    // We must set a default value e.g. YYYY-MM-DD-HH-MM-SS
    @Column(nullable = false)
    open var lastFilename: String,

    @Column(nullable = false)
    open var currentVersion: Long,

    @Column(nullable = false)
    open var organization: String,

    @Column(nullable = true)
    open var department: String?,

    @Column(nullable = false)
    open var sharingScope: SharingScope = SharingScope.PERSONAL,

    // JSON Managed Reference to handle bidirectional relationship -> Is the side to be serialized
    @JsonManagedReference
    @OneToMany(mappedBy = "file", cascade = [CascadeType.ALL], orphanRemoval = true)
    open var versions: MutableSet<FileVersion> = mutableSetOf(),

    @OneToMany(mappedBy = "file")
    open var shares: MutableSet<FileShare> = mutableSetOf(),

    ){

    fun addNewVersion(newVersion: FileVersion) {
        val nextVersionNumber = versions.maxOfOrNull { it.version }?.plus(1) ?: 1L
        newVersion.version = nextVersionNumber
        newVersion.file = this
        this.currentVersion = nextVersionNumber
        this.lastFilename = newVersion.filename
        this.lastUpdateAt = newVersion.lastUpdateAt
        this.versions.add(newVersion)
    }

    fun addNewShare(newShare: FileShare) {
        newShare.file = this
        this.shares.add(newShare)
    }

    fun toDTO(ownerName: String? = null): FileDTO {
        // Get the latest version to extract size and contentType
        val latestVersion = this.versions.maxByOrNull { it.version }

        return FileDTO(
            id = this.uuid,
            createdAt = RFC9110(this.createdAt.toInstant()),
            completedAt = RFC9110(this.lastUpdateAt.toInstant()),
            fileName = this.lastFilename,
            version = this.currentVersion,
            size = latestVersion?.size,
            contentType = latestVersion?.contentType,
            owner = this.ownerId,
            ownerName = ownerName,
            sharingScope = this.sharingScope
        )

    }
}