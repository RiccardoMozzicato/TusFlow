package g11.tusuploaderservice.entities

import com.fasterxml.jackson.annotation.JsonBackReference
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.TemporalType
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.Temporal
import java.util.Date

enum class FileStatus {
    MERGE_PENDING,
    AVAILABLE,
    DELETED
}

@Entity
@Table(name = "file_versions")
open class FileVersion(

    //Fare autoincrement
    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    @Column(updatable = false, nullable = false)
    open var id: Long? = null,

    @Column(nullable = false)
    open var version: Long = 1L,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    open var status: FileStatus,

    // JSON Back Reference to handle bidirectional relationship -> Is the side to be ignored during serialization
    @JsonBackReference
    @ManyToOne
    open var file: File? = null,

    @Column(nullable = false)
    open var filename: String,

    @Column
    open var size: Long,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    open var metadata: Map<String, String>?,

    @Column
    open var checksum: String?,

    @Column(nullable = false)
    open var contentType: String,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date,

    @Column(nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var lastUpdateAt: Date,

    @Column(nullable = false)
    open var objectKey: String
)