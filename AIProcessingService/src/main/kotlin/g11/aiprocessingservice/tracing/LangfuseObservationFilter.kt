package g11.aiprocessingservice.tracing

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.observation.ChatModelObservationContext
import org.springframework.stereotype.Component

/**
 * Adds gen_ai.prompt and gen_ai.completion as high-cardinality span attributes so that
 * Langfuse (and other OpenTelemetry backends) can display the full prompt and completion
 * text on each generation span.
 *
 * Required because Spring AI's default observation convention omits message content from
 * spans unless explicitly included. The filter reads directly from the observation context,
 * which is always populated regardless of the log-prompt / include-prompt properties.
 *
 * Reference: https://langfuse.com/docs/integrations/spring-ai
 */
@Component
class LangfuseObservationFilter : ObservationFilter {

    override fun map(context: Observation.Context): Observation.Context {
        if (context !is ChatModelObservationContext) return context

        val prompts = context.request?.instructions
            ?.mapNotNull { it.text }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        val completions = context.response?.results
            ?.mapNotNull { it.output?.text }
            ?.filter { it.isNotBlank() }
            ?: emptyList()

        if (prompts.isNotEmpty()) {
            context.addHighCardinalityKeyValue(
                KeyValue.of("gen_ai.prompt", prompts.joinToString("\n"))
            )
        }
        if (completions.isNotEmpty()) {
            context.addHighCardinalityKeyValue(
                KeyValue.of("gen_ai.completion", completions.joinToString("\n"))
            )
        }

        return context
    }
}
