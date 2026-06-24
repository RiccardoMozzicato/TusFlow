package g11.aiprocessingservice.conf

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

/**
 * STOMP-over-WebSocket configuration for pushing live AI-job status updates to the browser.
 *
 * The browser connects through the ApiGateway (which routes the `/api/v1/jobs` paths here and relays
 * the OAuth2 token). The handshake request is therefore authenticated by the resource-server filter
 * chain, so the STOMP session carries a Principal whose name is the JWT `sub` — the same UUID stored
 * as a file's owner. That lets [g11.aiprocessingservice.services.JobStatusService] push terminal
 * job outcomes to exactly that user via `convertAndSendToUser(ownerUserId, "/queue/jobs", ...)`.
 *
 * SockJS is enabled because the servlet gateway does not proxy the raw WebSocket upgrade; SockJS
 * falls back to HTTP streaming transports, which the gateway proxies like any other response.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig(
    // The browser reaches this endpoint through the gateway, so its Origin is the gateway's
    // (e.g. http://localhost:8080), never this service's own host. Spring's SockJS handshake
    // rejects cross-origin requests with 403 unless the gateway origin is explicitly allowed.
    @Value("\${websocket.allowed-origins:http://localhost:8080}") private val allowedOrigins: Array<String>,
) : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/api/v1/jobs/ws")
            .setAllowedOrigins(*allowedOrigins)
            .withSockJS()
    }

    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        // In-memory broker: per-user destinations only (clients subscribe, never publish).
        registry.enableSimpleBroker("/queue")
        registry.setUserDestinationPrefix("/user")
        // Preserve per-session publish order. The clientOutboundChannel is a multi-threaded executor
        // that otherwise may deliver messages out of order — harmless for sparse job-status pushes,
        // but the RAG stream fires many SOURCES/TOKEN frames back-to-back, so without this the answer
        // chunks arrive scrambled on the client. This serializes delivery per session (small
        // throughput cost) so frames land in the order they were published.
        registry.setPreservePublishOrder(true)
    }
}
