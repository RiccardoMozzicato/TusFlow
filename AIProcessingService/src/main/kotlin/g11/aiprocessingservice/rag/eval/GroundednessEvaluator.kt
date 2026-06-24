package g11.aiprocessingservice.rag.eval

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component

/**
 * LLM-as-judge: scores how well an answer is grounded in the retrieved context, in [0,1].
 * Used only when groundedness scoring is enabled (it costs one extra LLM call per query).
 */
@Component
class GroundednessEvaluator(
    @Qualifier("openAiChatModel") chatModel: OpenAiChatModel,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)
    private val chatClient = ChatClient.builder(chatModel).build()

    /** Returns a groundedness score in [0,1], or null if the judge could not be evaluated. */
    fun evaluate(context: String, answer: String): Double? {
        if (context.isBlank() || answer.isBlank()) return null
        return try {
            val raw = chatClient.prompt()
                .user(
                    """
                    You are evaluating whether an ANSWER is grounded in the provided CONTEXT.
                    Reply with ONLY a number between 0 and 1 (e.g. 0.0, 0.5, 1.0):
                    1 = fully supported by the context, 0 = not supported at all.

                    CONTEXT:
                    $context

                    ANSWER:
                    $answer
                    """.trimIndent()
                )
                .call()
                .content()
            parseScore(raw)
        } catch (e: Exception) {
            log.warn("Groundedness evaluation failed: {}", e.message)
            null
        }
    }

    // The judge is asked for a bare number, but tolerate surrounding text by extracting the first.
    private fun parseScore(raw: String?): Double? {
        val match = raw?.let { Regex("""\d*\.?\d+""").find(it)?.value } ?: return null
        return match.toDoubleOrNull()?.coerceIn(0.0, 1.0)
    }
}
