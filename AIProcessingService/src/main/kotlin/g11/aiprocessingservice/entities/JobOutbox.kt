package g11.aiprocessingservice.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "job_outbox")
open class JobOutbox(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    open var id: UUID? = null,

    @Column(name = "event_type", nullable = false)
    open var event_type: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    open var payload: Map<String, Any>, // this is the enhanced METADATA

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now()
)

