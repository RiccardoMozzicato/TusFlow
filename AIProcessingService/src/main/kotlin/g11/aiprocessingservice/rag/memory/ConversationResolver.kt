package g11.aiprocessingservice.rag.memory

import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the chat-memory key for a request and reports whether that conversation already has
 * history.
 *
 * The key is namespaced as `"{userId}:{conversationId}"` so two users can never share a memory
 * thread even if a client supplies (or guesses) another user's conversationId — `userId` comes from
 * the authenticated JWT. A missing or malformed client id starts a fresh (isolated) conversation.
 */
@Component
class ConversationResolver(
    private val chatMemory: ChatMemory,
) {
    fun memoryKey(userId: UUID, conversationId: String?): String {
        val conversation = conversationId?.takeIf(::isUuid) ?: UUID.randomUUID().toString()
        return "$userId:$conversation"
    }

    /** True when the conversation already has stored turns — used to enable history compression. */
    fun hasHistory(memoryKey: String): Boolean = chatMemory.get(memoryKey).isNotEmpty()

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value) }.isSuccess
}
