package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.rag.memory.ConversationResolver
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.memory.ChatMemory
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.messages.Message
import java.util.UUID

class ConversationResolverTest {

    private val chatMemory = mockk<ChatMemory>()
    private val resolver = ConversationResolver(chatMemory)
    private val userId = UUID.randomUUID()

    @Test
    fun `namespaces a valid conversationId under the user`() {
        val conversationId = UUID.randomUUID().toString()
        assertEquals("$userId:$conversationId", resolver.memoryKey(userId, conversationId))
    }

    @Test
    fun `mints a fresh conversation when the id is missing or malformed`() {
        listOf(null, "not-a-uuid").forEach { raw ->
            val key = resolver.memoryKey(userId, raw)
            assertTrue(key.startsWith("$userId:"))
            assertDoesNotThrow { UUID.fromString(key.substringAfter("$userId:")) }
        }
    }

    @Test
    fun `two users cannot share a thread even with the same conversationId`() {
        val conversationId = UUID.randomUUID().toString()
        val otherUser = UUID.randomUUID()
        assertNotEquals(
            resolver.memoryKey(userId, conversationId),
            resolver.memoryKey(otherUser, conversationId),
        )
    }

    @Test
    fun `hasHistory reflects stored messages`() {
        every { chatMemory.get("empty") } returns emptyList()
        every { chatMemory.get("nonEmpty") } returns listOf<Message>(AssistantMessage("hi"))

        assertFalse(resolver.hasHistory("empty"))
        assertTrue(resolver.hasHistory("nonEmpty"))
    }
}
