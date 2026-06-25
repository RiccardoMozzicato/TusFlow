package g11.aiprocessingservice.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Cross-stage pipeline metrics shared by the AIProcessingService Kafka consumers, exported to the
 * LGTM Prometheus via OTLP.
 *
 * - **event lag** (`pipeline.event.lag`): how long a record waited between being produced and being
 *   picked up here (now − Kafka record timestamp), tagged by topic. This is the cross-stage queue
 *   wait — it rises when a stage falls behind, complementing the per-stage processing timers.
 * - **file download** (`ai.file.download.duration`): latency of pulling a file from TusUploaderService,
 *   tagged by the pipeline `stage` that issued the download (analysis vs embedding).
 * - **dead-letter** (`ai.deadletter`): a unified counter (tagged by source topic) for records parked
 *   on a DLT after their retries were exhausted.
 *
 * Recording is best-effort and must never affect message processing. Per-tag meters are cached so
 * each unique tag set is registered once.
 */
@Component
class PipelineMetrics(private val registry: MeterRegistry) {

    private val lagTimers = ConcurrentHashMap<String, Timer>()
    private val downloadTimers = ConcurrentHashMap<String, Timer>()

    /** Records the age of a consumed record (now − its Kafka [recordTimestampMs]), tagged by [topic]. */
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

    /** Times a file download issued by [stage] (analysis|embedding) and returns the block's result. */
    fun <T> recordDownload(stage: String, block: () -> T): T {
        val timer = downloadTimers.getOrPut(stage) {
            Timer.builder("ai.file.download.duration")
                .description("Time to download a file from TusUploaderService, by pipeline stage")
                .tag("stage", stage)
                .publishPercentileHistogram()
                .register(registry)
        }
        val sample = Timer.start()
        try {
            return block()
        } finally {
            sample.stop(timer)
        }
    }

    /** Counts one record/batch dead-lettered after retries, tagged by its source [topic]. */
    fun countDeadLetter(topic: String) {
        Counter.builder("ai.deadletter")
            .description("Records parked on a dead-letter topic after retries, by source topic")
            .tag("topic", topic)
            .register(registry)
            .increment()
    }
}
