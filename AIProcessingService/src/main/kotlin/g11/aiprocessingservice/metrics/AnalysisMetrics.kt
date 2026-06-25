package g11.aiprocessingservice.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** Terminal outcome of an AI metadata-analysis job, used as the `outcome` tag. */
enum class AnalysisOutcome(val tag: String) {
    COMPLETED("completed"),
    UNSUPPORTED("unsupported"),
    FAILED("failed"),
}

/**
 * Micrometer metrics for the AI metadata-analysis stage (MetadataProcessor), exported to the LGTM
 * Prometheus via OTLP.
 *
 * `ai.analysis.duration` times the whole analysis — file download plus the LLM call — and is tagged
 * by the `client` that handled it (generic|pdf|image, or `none` when the type was rejected before a
 * client was chosen) and the terminal `outcome`. Being a Timer it also yields a `_count`, so it
 * doubles as the completed/unsupported/failed funnel without a separate counter. Recording is
 * best-effort and must never affect job processing.
 */
@Component
class AnalysisMetrics(private val registry: MeterRegistry) {

    private val timers = ConcurrentHashMap<String, Timer>()

    /** Records one analysis of [durationMs] ms handled by [client], with its terminal [outcome]. */
    fun recordAnalysis(client: String, outcome: AnalysisOutcome, durationMs: Long) {
        timers.getOrPut("$client|${outcome.tag}") {
            Timer.builder("ai.analysis.duration")
                .description("Wall-clock time of AI metadata analysis (download + LLM), by client and outcome")
                .tag("client", client)
                .tag("outcome", outcome.tag)
                .publishPercentileHistogram()
                .register(registry)
        }.record(durationMs, TimeUnit.MILLISECONDS)
    }
}
