package g11.aiprocessingservice.rag.dto

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Type of a RAG stream frame pushed over STOMP.")
enum class RagStreamEventType { SOURCES, TOKEN, DONE, ERROR }

/**
 * One frame of a streamed RAG answer, pushed over STOMP to `/user/queue/rag` and correlated by
 * [streamId]. A stream emits one SOURCES frame, then TOKEN frames, then a terminal DONE (or ERROR).
 */
@Schema(description = "One streamed RAG answer frame pushed to `/user/queue/rag` over STOMP.")
data class RagStreamEvent(
    @Schema(description = "Correlation id returned by `POST /api/rag/query/stream`.", example = "0c81a3e3-8e39-44fa-aabd-bf5b2c7a5b4d")
    val streamId: String,
    @Schema(description = "Frame type: SOURCES, TOKEN, DONE or ERROR.")
    val type: RagStreamEventType,
    @Schema(description = "Answer text fragment for TOKEN frames.", example = "Il documento indica che ")
    val token: String? = null,
    @Schema(description = "Retrieved sources for the answer, present on SOURCES frames.")
    val sources: List<DocumentSource>? = null,
    @Schema(description = "Error message, present on ERROR frames.", example = "stream failed")
    val error: String? = null,
) {
    companion object {
        fun sources(streamId: String, sources: List<DocumentSource>) =
            RagStreamEvent(streamId, RagStreamEventType.SOURCES, sources = sources)

        fun token(streamId: String, token: String) =
            RagStreamEvent(streamId, RagStreamEventType.TOKEN, token = token)

        fun done(streamId: String) = RagStreamEvent(streamId, RagStreamEventType.DONE)

        fun error(streamId: String, error: String) =
            RagStreamEvent(streamId, RagStreamEventType.ERROR, error = error)
    }
}
