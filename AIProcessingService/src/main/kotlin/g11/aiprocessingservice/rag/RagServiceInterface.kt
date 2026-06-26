package g11.aiprocessingservice.rag

import java.util.UUID

/**
 * Agentic RAG over the files a user can access (owned + shared), with conversational memory.
 * Exposes a synchronous query and a streaming variant; the implementation owns the chat-model
 * orchestration, retrieval tooling and tracing, so callers depend on this abstraction only.
 */
interface RagServiceInterface {

    /**
     * Answers [userQuery] with RAG across the caller's accessible files, using the conversation
     * identified by [conversationId] for memory (a fresh one is used if null/invalid).
     *
     * @param topK number of top similar chunks the content-search tool retrieves per call
     */
    fun queryWithRag(userQuery: String, conversationId: String?, topK: Int = 5): RagResponse

    /**
     * Streaming variant of [queryWithRag]: pushes a SOURCES frame, then TOKEN frames, then DONE/ERROR
     * to the user's STOMP queue, correlated by [streamId]. Runs asynchronously; the user identity is
     * resolved on the request thread and passed in, since the SecurityContext is not available on the
     * async thread.
     */
    fun streamQuery(
        userQuery: String,
        conversationId: String?,
        topK: Int,
        userId: UUID,
        userToken: String,
        streamId: String,
    )
}
