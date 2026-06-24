package g11.aiprocessingservice.rag.dto

/**
 * Response to `POST /api/rag/query/stream`: the id used to correlate the streamed events that follow
 * on the `/user/queue/rag` STOMP destination.
 */
data class RagStreamHandle(val streamId: String)
