package g11.aiprocessingservice.rag.retrieval

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Component

/**
 * Builds the conversational-memory advisor for RAG requests.
 *
 * Content retrieval is no longer an advisor — in the agentic flow the model pulls context on demand
 * via the `searchDocumentContent` tool ([g11.aiprocessingservice.rag.retrieval.DocumentSearchService]).
 * Query reformulation (resolving "it/that" against history, rephrasing for retrieval) is handled by
 * the model itself when it forms the tool's query argument, so the old
 * compression/rewrite/expansion transformers are gone. Memory stays an advisor so prior turns are
 * injected into the prompt before generation.
 */
@Component
class RagAdvisorFactory(
    private val chatMemory: ChatMemory,
) {
    /** Injects/saves conversation history for [memoryKey]. */
    fun memoryAdvisor(memoryKey: String): Advisor =
        MessageChatMemoryAdvisor.builder(chatMemory)
            .conversationId(memoryKey)
            .build()
}
