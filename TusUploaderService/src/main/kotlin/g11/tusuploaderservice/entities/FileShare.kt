package g11.tusuploaderservice.entities

import g11.tusuploaderservice.dto.FileShareDTO
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.springframework.data.jpa.repository.Temporal
import java.util.UUID
import g11.tusuploaderservice.utils.RFC9110
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import java.util.Date


enum class PERMISSION_LEVEL {
    READ_ONLY,
    READ_WRITE
}

@Entity
@Table(name = "file_shares")
open class FileShare(

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    open var id: Long? = null,

    @Column(nullable = false, updatable = false)
    open var userID: UUID,

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    open var permission: PERMISSION_LEVEL,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var timestamp: Date,

    @ManyToOne(fetch = FetchType.EAGER) // The Eager approach is preferred because the File data is frequently needed to be read.
    open var file: File
)

fun FileShare.toDTO(userName: String? = null): FileShareDTO {
    return FileShareDTO(
        id = this.id,
        fileID = this.file.uuid,
        userID = this.userID,
        permission = this.permission,
        timestamp = RFC9110(this.timestamp.toInstant()),
        userName = userName
    )
}