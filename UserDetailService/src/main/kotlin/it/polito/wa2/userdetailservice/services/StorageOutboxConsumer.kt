package it.polito.wa2.userdetailservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.tracing.Tracer
import it.polito.wa2.userdetailservice.entities.ProcessedStorageEvent
import it.polito.wa2.userdetailservice.metrics.StorageEventOutcome
import it.polito.wa2.userdetailservice.metrics.UserDetailMetrics
import it.polito.wa2.userdetailservice.repositories.ProcessedStorageEventRepository
import it.polito.wa2.userdetailservice.repositories.UserRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class StorageOutboxConsumer(
    private val userRepository: UserRepository,
    private val processedStorageEventRepository: ProcessedStorageEventRepository,
    private val tracer: Tracer,
    private val metrics: UserDetailMetrics,
) {
    private val log = LoggerFactory.getLogger(StorageOutboxConsumer::class.java)
    private val objectMapper = jacksonObjectMapper()

    @KafkaListener(
        id = "userdetail-storage-consumer",
        topics = ["file-outbox"],
        containerFactory = "debeziumKafkaListenerContainerFactory"
    )
    @Transactional
    fun onMessage(
        message: String,
        acknowledgment: Acknowledgment,
        @Header(name = KafkaHeaders.RECEIVED_TIMESTAMP, required = false) receivedTimestamp: Long,
    ) {
        metrics.recordEventLag("file-outbox", receivedTimestamp)
        metrics.recordProcessing { handleMessage(message, acknowledgment) }
    }

    private fun handleMessage(message: String, acknowledgment: Acknowledgment) {
        log.debug("Received storage outbox message: {}", message)

        val root = objectMapper.readTree(message)
        val eventType = root.get("event_type")?.asText()
        if (eventType == null) {
            log.warn("Skipping message without event_type")
            metrics.countEvent("none", StorageEventOutcome.IGNORED)
            acknowledgment.acknowledge()
            return
        }

        if (eventType !in setOf("STORAGE_RESERVED", "STORAGE_CONFIRMED", "STORAGE_RELEASED")) {
            log.debug("Ignoring storage event type: {}", eventType)
            metrics.countEvent(eventType, StorageEventOutcome.IGNORED)
            acknowledgment.acknowledge()
            return
        }

        val payloadNode = root.get("payload")
        val payload = when {
            payloadNode == null -> null
            payloadNode.isTextual -> objectMapper.readTree(payloadNode.asText())
            payloadNode.isObject -> payloadNode
            else -> null
        }

        // Trace the storage-counter update as its OWN trace (a fresh root) correlated to the upload by
        // `file.id` — NOT as a continuation of the upload's HTTP trace. The upload POST/PATCH is a short
        // request that finishes ~hundreds of ms before this Debezium event arrives; continuing its trace
        // appended a late tail that left Tempo reporting "<root span not yet received>" for the upload.
        // Tagging `file.id` (the same attribute the upload request spans carry) keeps the
        // `{ .file.id = "<uuid>" }` query grouping the upload and its storage update together, and the
        // active span keeps traceId/spanId in the MDC for log correlation.
        val span = tracer.nextSpan().name("storage-update file-outbox").start()
        payload?.get("fileId")?.asText()?.takeIf { it.isNotBlank() }?.let { span.tag("file.id", it) }
        val scope = tracer.withSpan(span)
        try {
            val ownerUserId = payload?.get("ownerUserId")?.asText()
            val size = payload?.get("size")?.asLong()
            if (ownerUserId.isNullOrBlank() || size == null) {
                log.warn("Skipping invalid storage event payload (missing ownerUserId or size): {}", payloadNode)
                metrics.countEvent(eventType, StorageEventOutcome.INVALID)
                acknowledgment.acknowledge()
                return
            }

            val user = userRepository.findById(UUID.fromString(ownerUserId)).getOrNull()
            if (user == null) {
                log.warn("Skipping storage event with invalid ownerUserId UUID: {}", ownerUserId)
                metrics.countEvent(eventType, StorageEventOutcome.INVALID)
                acknowledgment.acknowledge()
                return
            }

            // Idempotency guard: the outbox event id uniquely identifies this event. Storage deltas are
            // a read-modify-write, so applying the same event twice (at-least-once delivery, rebalance,
            // retry) would drift the counter. We record the event id and the counter update in the same
            // transaction, so either both commit or neither does.
            val eventId = root.get("id")?.asText()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            if (eventId == null) {
                log.warn("Skipping storage event without a valid id: {}", message)
                metrics.countEvent(eventType, StorageEventOutcome.INVALID)
                acknowledgment.acknowledge()
                return
            }
            if (processedStorageEventRepository.existsById(eventId)) {
                log.debug("Skipping already-processed storage event {}", eventId)
                metrics.countEvent(eventType, StorageEventOutcome.DUPLICATE)
                acknowledgment.acknowledge()
                return
            }
            try {
                // The primary key is the real guard against concurrent duplicates: the existsById check
                // above is only a fast path, this insert is what serializes two concurrent deliveries.
                processedStorageEventRepository.save(ProcessedStorageEvent(eventId))
                processedStorageEventRepository.flush()
            } catch (e: DataIntegrityViolationException) {
                log.debug("Storage event {} already processed concurrently, skipping", eventId, e)
                metrics.countEvent(eventType, StorageEventOutcome.DUPLICATE)
                acknowledgment.acknowledge()
                return
            }

            val currentUsed = user.storageUsed
            when (eventType) {
                "STORAGE_RESERVED" -> {
                    user.storageUsed = currentUsed + size }
                "STORAGE_RELEASED" -> {user.storageUsed = (currentUsed - size).coerceAtLeast(0)}
                "STORAGE_CONFIRMED" -> {
                    log.debug("Storage used confirmed for user {}: size {}.", ownerUserId, size)
                    // Already counted on reservation.
                }
            }

            // Force the storage_used UPDATE (and its @Version optimistic-lock check) to run now, before
            // we acknowledge. If another transaction modified this user's row concurrently, this throws
            // ObjectOptimisticLockingFailureException: the whole transaction — including the dedup insert
            // above — rolls back, the message stays unacknowledged, and Kafka redelivers it so the
            // read-modify-write retries against the fresh row.
            userRepository.flush()

            metrics.countEvent(eventType, StorageEventOutcome.APPLIED)
            acknowledgment.acknowledge()
        } finally {
            scope.close()
            span.end()
        }
    }
}
