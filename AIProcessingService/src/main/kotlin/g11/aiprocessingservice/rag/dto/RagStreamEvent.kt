package g11.aiprocessingservice.rag.dto

enum class RagStreamEventType { SOURCES, TOKEN, DONE, ERROR }

/**
 * One frame of a streamed RAG answer, pushed over STOMP to `/user/queue/rag` and correlated by
 * [streamId]. A stream emits one SOURCES frame, then TOKEN frames, then a terminal DONE (or ERROR).
 */
data class RagStreamEvent(
    val streamId: String,
    val type: RagStreamEventType,
    val token: String? = null,
    val sources: List<DocumentSource>? = null,
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
