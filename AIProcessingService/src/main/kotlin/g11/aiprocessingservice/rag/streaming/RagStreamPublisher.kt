package g11.aiprocessingservice.rag.streaming

import g11.aiprocessingservice.rag.dto.RagStreamEvent
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Pushes streamed RAG events to the requesting user's STOMP queue (`/user/queue/rag`), mirroring
 * JobStatusService's per-user broadcast. `convertAndSendToUser` targets the user by JWT `sub`, which
 * matches the STOMP session Principal. Best-effort: a messaging failure is logged, never thrown.
 */
@Component
class RagStreamPublisher(private val messagingTemplate: SimpMessagingTemplate) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    fun publish(userId: UUID, event: RagStreamEvent) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), "/queue/rag", event)
        } catch (e: Exception) {
            log.warn("Failed to publish RAG stream event {} for user {}: {}", event.type, userId, e.message)
        }
    }
}
