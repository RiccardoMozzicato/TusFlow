package g11.tusuploaderservice.entities

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.Temporal
import java.util.Date
import java.util.UUID

@Entity
@Table(name = "files_outbox_events")
open class FilesOutboxEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    open var id: UUID? = null,

    @Column(name = "event_type", nullable = false)
    open var eventType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    open var payload: Map<String, Any?>,

    @Column(name = "created_at", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var createdAt: Date
)
