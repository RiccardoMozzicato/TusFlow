package g11.aiprocessingservice.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/**
 * Micrometer metrics for the embedding-preparation stage (EmbeddingProcessor), exported to the LGTM
 * Prometheus via OTLP.
 *
 * Covers the work between a file-analyzed event and the embeddings-batch records: text extraction
 * (timed by content `type`), the extracted text length, the number of chunks produced per file, and
 * the count of batches published — the upstream funnel counterpart to `ai.embedding.chunks`.
 * Recording is best-effort and must never affect message processing.
 */
@Component
class ExtractionMetrics(private val registry: MeterRegistry) {

    private val extractionTimers = ConcurrentHashMap<String, Timer>()

    private val textLength: DistributionSummary = DistributionSummary.builder("ai.document.text.length")
        .description("Extracted text length in characters, per file")
        .publishPercentileHistogram()
        .register(registry)

    private val chunkCount: DistributionSummary = DistributionSummary.builder("ai.document.chunks")
        .description("Chunks produced per file")
        .publishPercentileHistogram()
        .register(registry)

    private val batches: Counter = Counter.builder("ai.embedding.batches")
        .description("Embedding batches published to the embeddings-batch topic")
        .register(registry)

    /** Times text extraction for content [type] (base MIME) and returns the block's result. */
    fun <T> recordExtraction(type: String, block: () -> T): T {
        val timer = extractionTimers.getOrPut(type) {
            Timer.builder("ai.extraction.duration")
                .description("Time to extract text from a file, by content type")
                .tag("type", type)
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

    /** Records the extracted text length in characters for one file. */
    fun recordTextLength(chars: Int) = textLength.record(chars.toDouble())

    /** Records the number of chunks produced for one file. */
    fun recordChunks(count: Int) = chunkCount.record(count.toDouble())

    /** Counts the [count] batches published for one file. */
    fun countBatches(count: Int) = batches.increment(count.toDouble())
}
