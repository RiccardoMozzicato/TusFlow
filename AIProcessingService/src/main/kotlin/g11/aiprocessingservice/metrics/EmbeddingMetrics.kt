package g11.aiprocessingservice.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component

/** Outcome of processing a single chunk, used as the `outcome` tag on the chunk counter. */
enum class ChunkOutcome(val tag: String) {
    EMBEDDED("embedded"),
    SKIPPED("skipped"),
    FAILED("failed"),
}

/**
 * Micrometer metrics for the embedding-generation stage, exported to the LGTM Prometheus via OTLP.
 *
 * Complements the Langfuse trace tags in PipelineTracing: a trace explains one batch, these meters
 * aggregate throughput and error/skip rate across all batches for dashboards and alerts. Meters are
 * registered once on construction; recording is best-effort and must never affect pipeline work.
 */
@Component
class EmbeddingMetrics(registry: MeterRegistry) {

    private val batchTimer: Timer = Timer.builder("ai.embedding.batch.duration")
        .description("Wall-clock time to process one embedding chunk batch")
        .publishPercentileHistogram()
        .register(registry)

    private val chunkCounters: Map<ChunkOutcome, Counter> = ChunkOutcome.values().associateWith { outcome ->
        Counter.builder("ai.embedding.chunks")
            .description("Chunks processed by the embedding generator, by outcome")
            .tag("outcome", outcome.tag)
            .register(registry)
    }

    /** Times [block] (one batch) and records it against the batch-duration timer. */
    fun recordBatch(block: () -> Unit) {
        val sample = Timer.start()
        try {
            block()
        } finally {
            sample.stop(batchTimer)
        }
    }

    /** Counts one processed chunk under its [outcome]. */
    fun countChunk(outcome: ChunkOutcome) {
        chunkCounters.getValue(outcome).increment()
    }
}
