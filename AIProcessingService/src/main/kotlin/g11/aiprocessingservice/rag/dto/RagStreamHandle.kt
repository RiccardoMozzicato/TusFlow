package g11.aiprocessingservice.rag.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Response to `POST /api/rag/query/stream`: the id used to correlate the streamed events that follow
 * on the `/user/queue/rag` STOMP destination.
 */
@Schema(description = "Handle returned immediately after accepting a streamed RAG query.")
data class RagStreamHandle(
    @Schema(description = "Correlation id for frames pushed to `/user/queue/rag`.", example = "0c81a3e3-8e39-44fa-aabd-bf5b2c7a5b4d")
    val streamId: String,
)
