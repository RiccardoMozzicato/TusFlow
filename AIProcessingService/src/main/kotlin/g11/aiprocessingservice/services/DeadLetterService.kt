package g11.aiprocessingservice.services

import g11.aiprocessingservice.entities.DeadLetterEvent
import g11.aiprocessingservice.repositories.DeadLetterEventRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets

/**
 * Persists dead-lettered Kafka records to the `dead_letter_events` audit table so failures that
 * exhausted their retries are durably inspectable. Invoked by the per-pipeline `@DltHandler`s.
 */
@Service
class DeadLetterService(private val repository: DeadLetterEventRepository) {

    private val log = LoggerFactory.getLogger(DeadLetterService::class.java)

    /**
     * Records one dead-lettered Kafka record. The exception headers arrive as raw byte[] (as written
     * by Spring's DeadLetterPublishingRecoverer), so they are decoded to UTF-8 and delegated.
     */
    @Transactional
    fun record(
        originalTopic: String,
        payload: String,
        exceptionType: ByteArray?,
        exceptionMessage: ByteArray?,
    ) = record(
        originalTopic,
        payload,
        exceptionType?.toString(StandardCharsets.UTF_8),
        exceptionMessage?.toString(StandardCharsets.UTF_8),
    )

    /**
     * Records one dead-lettered message with already-decoded exception details. Used by the terminal
     * user-retry-exhaustion path, which holds the Throwable directly rather than Kafka byte[] headers.
     */
    @Transactional
    fun record(
        originalTopic: String,
        payload: String,
        exceptionType: String?,
        exceptionMessage: String?,
    ) {
        log.error("Dead letter from '{}' [{}]: {}", originalTopic, exceptionType, exceptionMessage)

        repository.save(
            DeadLetterEvent(
                originalTopic = originalTopic,
                payload = payload,
                exceptionType = exceptionType,
                exceptionMessage = exceptionMessage,
            ),
        )
    }
}
