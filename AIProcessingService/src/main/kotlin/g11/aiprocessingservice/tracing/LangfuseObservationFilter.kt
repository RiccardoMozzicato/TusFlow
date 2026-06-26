package g11.aiprocessingservice.tracing

import io.micrometer.common.KeyValue
import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationFilter
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import org.springframework.ai.chat.messages.ToolResponseMessage
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
 * Tool-call requests (assistant messages with no text, only tool calls) and tool results
 * (ToolResponseMessage, whose payload is in `responses`, not `text`) are rendered explicitly.
 * Otherwise they'd be stripped to blank and the agentic round-trips would be invisible in
 * Langfuse — every generation would look like just "system + user".
 *
 * Reference: https://langfuse.com/docs/integrations/spring-ai
 */
@Component
class LangfuseObservationFilter : ObservationFilter {

    override fun map(context: Observation.Context): Observation.Context {
        if (context !is ChatModelObservationContext) return context

        val prompt = context.request?.instructions
            ?.mapNotNull(::renderMessage)
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")

        val completion = context.response?.results
            ?.mapNotNull { it.output?.let(::renderMessage) }
            ?.filter { it.isNotBlank() }
            ?.joinToString("\n")

        if (!prompt.isNullOrBlank()) {
            context.addHighCardinalityKeyValue(KeyValue.of("gen_ai.prompt", prompt))
        }
        if (!completion.isNullOrBlank()) {
            context.addHighCardinalityKeyValue(KeyValue.of("gen_ai.completion", completion))
        }

        return context
    }

    /**
     * Renders a message to text including tool-call requests and tool results, so the agentic
     * round-trips are visible in Langfuse instead of being stripped down to bare text.
     */
    private fun renderMessage(message: Message): String = when (message) {
        is AssistantMessage -> buildString {
            message.text?.takeIf { it.isNotBlank() }?.let(::append)
            message.toolCalls.forEach { call ->
                if (isNotEmpty()) append('\n')
                append("[tool_call ${call.name}(${call.arguments})]")
            }
        }
        is ToolResponseMessage -> message.responses.joinToString("\n") { response ->
            "[tool_result ${response.name}: ${response.responseData}]"
        }
        else -> message.text ?: ""
    }
}
