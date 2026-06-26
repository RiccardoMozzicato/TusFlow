package it.polito.wa2.userdetailservice.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Outcome of processing one storage outbox event, used as the `outcome` tag. */
enum class StorageEventOutcome(val tag: String) {
    APPLIED("applied"),
    DUPLICATE("duplicate"),
    IGNORED("ignored"),
    INVALID("invalid"),
}

/**
 * Micrometer metrics for the storage outbox consumer, exported to the LGTM Prometheus via OTLP.
 *
 * The consumer applies storage-usage deltas from Debezium CDC events; these meters expose how fast
 * it drains the topic and the breakdown of applied vs duplicate/ignored/invalid events. Recording is
 * best-effort and must never affect message processing.
 */
@Component
class UserDetailMetrics(private val registry: MeterRegistry) {

    private val processingTimer: Timer = Timer.builder("userdetail.storage.event.duration")
        .description("Time to process one storage outbox message")
        .publishPercentileHistogram()
        .register(registry)

    private val lagTimers = ConcurrentHashMap<String, Timer>()

    /** Times the processing of one outbox message. */
    fun recordProcessing(block: () -> Unit) {
        val sample = Timer.start()
        try {
            block()
        } finally {
            sample.stop(processingTimer)
        }
    }

    /**
     * Records the age of a consumed record (now − its Kafka [recordTimestampMs]), tagged by [topic] —
     * the cross-stage queue wait for the storage outbox consumer.
     */
    fun recordEventLag(topic: String, recordTimestampMs: Long) {
        val lagMs = (System.currentTimeMillis() - recordTimestampMs).coerceAtLeast(0)
        lagTimers.getOrPut(topic) {
            Timer.builder("pipeline.event.lag")
                .description("Age of a consumed record at processing time (now − Kafka timestamp), by topic")
                .tag("topic", topic)
                .publishPercentileHistogram()
                .register(registry)
        }.record(lagMs, TimeUnit.MILLISECONDS)
    }

    /** Counts one processed event by its [eventType] and [outcome]. */
    fun countEvent(eventType: String, outcome: StorageEventOutcome) {
        Counter.builder("userdetail.storage.events")
            .description("Storage outbox events processed, by type and outcome")
            .tag("event_type", eventType)
            .tag("outcome", outcome.tag)
            .register(registry)
            .increment()
    }
}
