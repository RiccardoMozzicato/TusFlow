package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.rag.RagService
import g11.aiprocessingservice.rag.RagUserContext
import g11.aiprocessingservice.rag.dto.DocumentSource
import g11.aiprocessingservice.rag.dto.RagStreamEventType
import g11.aiprocessingservice.rag.memory.ConversationResolver
import g11.aiprocessingservice.rag.retrieval.RagAdvisorFactory
import g11.aiprocessingservice.rag.streaming.RagStreamPublisher
import g11.aiprocessingservice.rag.tools.RagToolContextKeys
import g11.aiprocessingservice.rag.tools.RagTools
import g11.aiprocessingservice.rag.tools.RetrievedSourcesCapture
import g11.aiprocessingservice.services.FileAccessService
import g11.aiprocessingservice.tracing.RagTracing
import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.ChatClientResponse
import org.springframework.ai.chat.client.advisor.api.Advisor
import org.springframework.ai.chat.messages.AssistantMessage
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import java.util.UUID

class RagServiceTest {

    private lateinit var chatClient: ChatClient
    private lateinit var advisorFactory: RagAdvisorFactory
    private lateinit var ragTools: RagTools
    private lateinit var fileAccessService: FileAccessService
    private lateinit var userContext: RagUserContext
    private lateinit var conversationResolver: ConversationResolver
    private lateinit var tracer: Tracer
    private lateinit var streamPublisher: RagStreamPublisher
    private lateinit var service: RagService

    private val userId = UUID.randomUUID()
    private val conversationId = UUID.randomUUID().toString()

    // Captures the per-request tool context so a test can both assert what was passed (topK, fileIds)
    // and simulate the search tool by populating the capture holder it carries.
    private val toolContextSlot = slot<Map<String, Any>>()

    @BeforeEach
    fun setup() {
        chatClient = mockk()
        advisorFactory = mockk()
        ragTools = mockk(relaxed = true)
        fileAccessService = mockk()
        userContext = mockk()
        conversationResolver = mockk()
        tracer = mockk(relaxed = true)
        streamPublisher = mockk(relaxed = true)
        every { userContext.currentUserId() } returns userId
        every { userContext.currentToken() } returns "token"
        every { conversationResolver.memoryKey(any(), any()) } returns "$userId:$conversationId"
        every { advisorFactory.memoryAdvisor(any()) } returns mockk<Advisor>(relaxed = true)
        // Real RagTracing over a relaxed tracer so traceQuery still executes the work lambda.
        service = RagService(
            chatClient, advisorFactory, ragTools, fileAccessService, userContext, conversationResolver,
            RagTracing(tracer), streamPublisher, mockk(relaxed = true),
        )
    }

    private fun source(filename: String, similarity: Double) = DocumentSource(
        content = "chunk text",
        contentPreview = "chunk text",
        metadata = mapOf("fileId" to "f1", "filename" to filename),
        similarity = similarity,
    )

    /**
     * Stubs the chatClient fluent chain to return [answer]. The model is mocked away, so the
     * `searchDocumentContent` tool never really runs — instead we populate the request's capture
     * holder with [retrieved] when `.call()` fires, simulating a search the model would have made.
     */
    private fun stubChatResponse(answer: String, retrieved: List<DocumentSource>) {
        val requestSpec = mockk<ChatClient.ChatClientRequestSpec>(relaxed = true)
        val callSpec = mockk<ChatClient.CallResponseSpec>()
        val response = ChatClientResponse(
            ChatResponse(listOf(Generation(AssistantMessage(answer)))),
            emptyMap(),
        )
        every { chatClient.prompt() } returns requestSpec
        every { requestSpec.advisors(*anyVararg()) } returns requestSpec
        every { requestSpec.tools(*anyVararg()) } returns requestSpec
        every { requestSpec.toolContext(capture(toolContextSlot)) } returns requestSpec
        every { requestSpec.user(any<String>()) } returns requestSpec
        every { requestSpec.call() } answers {
            captureHolder().add(retrieved)
            callSpec
        }
        every { callSpec.chatClientResponse() } returns response
    }

    private fun captureHolder() =
        toolContextSlot.captured[RagToolContextKeys.CAPTURE] as RetrievedSourcesCapture

    @Test
    fun `returns guidance and never calls the model when user has no accessible files`() {
        every { fileAccessService.getAccessibleFileIds(userId, "token") } returns emptySet()

        val response = service.queryWithRag("q", conversationId, 5)

        assertEquals(0, response.accessibleFilesCount)
        assertTrue(response.sources.isEmpty())
        verify(exactly = 0) { chatClient.prompt() }
    }

    @Test
    fun `propagates topK and accessible files into the tool context and passes sources through`() {
        every { fileAccessService.getAccessibleFileIds(userId, "token") } returns setOf("f1")
        stubChatResponse(answer = "Mocked answer", retrieved = listOf(source("doc.pdf", similarity = 0.8)))

        val response = service.queryWithRag("q", conversationId, 7)

        assertEquals("Mocked answer", response.answer)
        assertEquals(1, response.sourcesCount)
        assertEquals(0.8, response.sources[0].similarity, 1e-9)
        // The model-uncontrollable scope + topK reach the tools via the tool context.
        assertEquals(7, toolContextSlot.captured[RagToolContextKeys.TOP_K])
        assertEquals(setOf("f1"), toolContextSlot.captured[RagToolContextKeys.ACCESSIBLE_FILE_IDS])
    }

    @Test
    fun `returns the model answer with no sources when the model retrieves nothing`() {
        every { fileAccessService.getAccessibleFileIds(userId, "token") } returns setOf("f1")
        stubChatResponse(answer = "I don't have enough information to answer that", retrieved = emptyList())

        val response = service.queryWithRag("q", conversationId, 5)

        assertEquals(1, response.accessibleFilesCount)
        assertTrue(response.sources.isEmpty())
        assertEquals("I don't have enough information to answer that", response.answer)
    }

    @Test
    fun `streamQuery publishes sources, the answer, then done`() {
        every { fileAccessService.getAccessibleFileIds(userId, "token") } returns setOf("f1")
        // streamQuery resolves tools via a blocking .call() (Spring AI streaming tool-calling is
        // broken for Qwen-style backends; spring-ai#4790), then streams the finished answer.
        stubChatResponse(answer = "Hello", retrieved = listOf(source("doc.pdf", similarity = 0.9)))

        // Called directly (not via the @Async proxy); the call resolves synchronously.
        service.streamQuery("q", conversationId, 5, userId, "token", "stream-1")

        verify { streamPublisher.publish(userId, match { it.type == RagStreamEventType.SOURCES && it.sources?.size == 1 }) }
        verify { streamPublisher.publish(userId, match { it.type == RagStreamEventType.TOKEN && it.token == "Hello" }) }
        verify { streamPublisher.publish(userId, match { it.type == RagStreamEventType.DONE }) }
    }

    @Test
    fun `streamQuery ends early when the user has no accessible files`() {
        every { fileAccessService.getAccessibleFileIds(userId, "token") } returns emptySet()

        service.streamQuery("q", conversationId, 5, userId, "token", "stream-2")

        verify { streamPublisher.publish(userId, match { it.type == RagStreamEventType.DONE }) }
        verify(exactly = 0) { chatClient.prompt() }
    }
}
