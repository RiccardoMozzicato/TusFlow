package g11.aiprocessingservice.entities

import g11.aiprocessingservice.dto.jobStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Enumerated
import jakarta.persistence.EnumType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
@Table(name = "jobs")
open class Job(
    @Id
    @Column(name = "jobid")
    open var jobid: UUID,

    @Column(name = "fileid", nullable = false)
    open var fileid: UUID,

    @Column(name = "version", nullable = false)
    open var version: Long,

    @Column(name = "job_type", nullable = false)
    open var type: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    open var payload: Map<String, Any>?,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    open var status: jobStatus
)
