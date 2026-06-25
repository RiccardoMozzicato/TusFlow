package g11.tusuploaderservice.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Micrometer metrics for the tus upload flow, exported to the LGTM Prometheus via OTLP.
 *
 * A tus upload spans several HTTP requests (creation POST + one PATCH per chunk), so request-level
 * `http.server.requests` alone does not tell the upload story. These meters capture byte throughput
 * and the create → complete / terminate funnel. Recording is best-effort and must never break a
 * request; meters are registered once on construction.
 */
@Component
class TusMetrics(private val registry: MeterRegistry) {

    private val lagTimers = ConcurrentHashMap<String, Timer>()

    // No baseUnit: the OTLP exporter appends the unit to the metric name, and the names already
    // carry "bytes" — setting baseUnit("bytes") would export them as `..._bytes_bytes`.
    private val bytesReceived: Counter = Counter.builder("tus.upload.bytes")
        .description("Total bytes received across all upload chunks (POST + PATCH)")
        .register(registry)

    private val chunkSize: DistributionSummary = DistributionSummary.builder("tus.upload.chunk.bytes")
        .description("Size of each received upload chunk")
        .publishPercentileHistogram()
        .register(registry)

    private val created: Counter = Counter.builder("tus.uploads.created")
        .description("Upload resources created (one per creation POST)")
        .register(registry)

    private val completed: Counter = Counter.builder("tus.uploads.completed")
        .description("Uploads that reached their full length (offset == length)")
        .register(registry)

    private val terminated: Counter = Counter.builder("tus.uploads.terminated")
        .description("Uploads terminated by the client (DELETE) before/after completion")
        .register(registry)

    /** Records one received chunk of [bytes] bytes (counter total + size distribution). */
    fun recordChunk(bytes: Int) {
        bytesReceived.increment(bytes.toDouble())
        chunkSize.record(bytes.toDouble())
    }

    /** Counts one newly created upload resource. */
    fun countCreated() = created.increment()

    /** Counts one upload that reached its full length. */
    fun countCompleted() = completed.increment()

    /** Counts one client-terminated upload. */
    fun countTerminated() = terminated.increment()

    /**
     * Records the age of a consumed record (now − its Kafka [recordTimestampMs]), tagged by [topic].
     * This is the cross-stage queue wait for the job-outbox consumer — it rises when AIProcessingService
     * falls behind producing analysis results.
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

    /**
     * Counts a completion when [offset] has reached a known [length]. No-op while the length is
     * still unknown (deferred-length uploads) or the upload is not yet fully received.
     */
    fun countCompletionIfDone(offset: Long?, length: Long?) {
        if (length != null && offset != null && offset >= length) {
            countCompleted()
        }
    }
}
