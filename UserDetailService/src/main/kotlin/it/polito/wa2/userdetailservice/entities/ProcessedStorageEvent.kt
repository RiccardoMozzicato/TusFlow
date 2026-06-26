package it.polito.wa2.userdetailservice.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Temporal
import jakarta.persistence.TemporalType
import java.util.Date
import java.util.UUID


/**
 * Idempotency marker for storage outbox events that have already been applied to a user's
 * storage counter. The primary key is the unique outbox event id, so re-delivering the same
 * Kafka message cannot apply the storage delta more than once.
 */
@Entity
@Table(name = "processed_storage_events")
class ProcessedStorageEvent(

    @Id
    @Column(name = "event_id", updatable = false, nullable = false)
    open var eventId: UUID,

    @Column(name = "processed_at", updatable = false, nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    open var processedAt: Date = Date(),
)
