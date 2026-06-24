package g11.aiprocessingservice.rag.dto

/**
 * RAG query request body.
 *
 * @property query natural-language question
 * @property conversationId client-minted conversation id for chat memory (a fresh one is used when
 *   null). The server scopes memory per authenticated user, so this id only distinguishes a user's
 *   own conversations.
 * @property topK number of most-similar chunks to retrieve (falls back to the configured default)
 */
data class RagQueryRequest(
    val query: String,
    val conversationId: String? = null,
    val topK: Int? = null,
)
