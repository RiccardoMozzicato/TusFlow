package it.polito.wa2.userdetailservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import it.polito.wa2.userdetailservice.entities.ProcessedStorageEvent
import it.polito.wa2.userdetailservice.repositories.ProcessedStorageEventRepository
import it.polito.wa2.userdetailservice.repositories.UserRepository
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.jvm.optionals.getOrNull

@Service
class StorageOutboxConsumer(
    private val userRepository: UserRepository,
    private val processedStorageEventRepository: ProcessedStorageEventRepository,
) {
    private val log = LoggerFactory.getLogger(StorageOutboxConsumer::class.java)
    private val objectMapper = jacksonObjectMapper()

    @KafkaListener(
        id = "userdetail-storage-consumer",
        topics = ["file-outbox"],
        containerFactory = "debeziumKafkaListenerContainerFactory"
    )
    @Transactional
    fun onMessage(message: String, acknowledgment: Acknowledgment) {
        log.debug("Received storage outbox message: {}", message)

        val root = objectMapper.readTree(message)
        val eventType = root.get("event_type")?.asText()
        if (eventType == null) {
            log.warn("Skipping message without event_type")
            acknowledgment.acknowledge()
            return
        }

        if (eventType !in setOf("STORAGE_RESERVED", "STORAGE_CONFIRMED", "STORAGE_RELEASED")) {
            log.debug("Ignoring storage event type: {}", eventType)
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
        val ownerUserId = payload?.get("ownerUserId")?.asText()
        val size = payload?.get("size")?.asLong()
        if (ownerUserId.isNullOrBlank() || size == null) {
            log.warn("Skipping invalid storage event payload (missing ownerUserId or size): {}", payloadNode)
            acknowledgment.acknowledge()
            return
        }

        val user = userRepository.findById(UUID.fromString(ownerUserId)).getOrNull()
        if (user == null) {
            log.warn("Skipping storage event with invalid ownerUserId UUID: {}", ownerUserId)
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
            acknowledgment.acknowledge()
            return
        }
        if (processedStorageEventRepository.existsById(eventId)) {
            log.debug("Skipping already-processed storage event {}", eventId)
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

        acknowledgment.acknowledge()
    }
}
