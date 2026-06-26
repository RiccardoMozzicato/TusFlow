package g11.aiprocessingservice.entities

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

/**
 * Audit record of a Kafka message that exhausted its retries and was dead-lettered. Persisted by
 * the DLT handlers so failures are durably inspectable after the `-dlt` topic retention expires.
 */
@Entity
@Table(name = "dead_letter_events")
open class DeadLetterEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id")
    open var id: UUID? = null,

    @Column(name = "original_topic", nullable = false)
    open var originalTopic: String,

    @Column(name = "payload", columnDefinition = "text", nullable = false)
    open var payload: String,

    @Column(name = "exception_type")
    open var exceptionType: String? = null,

    @Column(name = "exception_message", columnDefinition = "text")
    open var exceptionMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    open var createdAt: Instant = Instant.now(),
)
