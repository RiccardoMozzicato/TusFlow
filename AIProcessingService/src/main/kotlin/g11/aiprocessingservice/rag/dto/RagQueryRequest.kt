package g11.aiprocessingservice.rag.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * RAG query request body.
 *
 * @property query natural-language question
 * @property conversationId client-minted conversation id for chat memory (a fresh one is used when
 *   null). The server scopes memory per authenticated user, so this id only distinguishes a user's
 *   own conversations.
 * @property topK number of most-similar chunks to retrieve (falls back to the configured default)
 */
@Schema(description = "Request body for a streamed RAG query.")
data class RagQueryRequest(
    @Schema(description = "Natural-language question to answer using accessible documents.", example = "Quali documenti parlano del budget del progetto?")
    val query: String,
    @Schema(description = "Client-generated conversation id used for per-user chat memory.", example = "dashboard-chat-2026-06-26")
    val conversationId: String? = null,
    @Schema(description = "Number of most-similar chunks the content-search tool retrieves per call.", example = "5")
    val topK: Int? = null,
)
