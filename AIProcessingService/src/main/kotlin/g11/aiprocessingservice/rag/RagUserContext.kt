package g11.aiprocessingservice.rag

import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Resolves the authenticated caller from the Spring Security context for the RAG endpoints.
 * Centralises the JWT extraction that was previously inlined in RagService / RagController.
 */
@Component
class RagUserContext {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * The current user's id, taken authoritatively from the JWT subject (never the request body).
     * @throws IllegalStateException if not JWT-authenticated; IllegalArgumentException if the subject
     *   is not a UUID.
     */
    fun currentUserId(): UUID {
        val subject = currentJwt().subject
        return try {
            UUID.fromString(subject)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("JWT subject is not a valid user UUID: $subject")
        }
    }

    /**
     * The current user's bearer token (used to call downstream services on the user's behalf).
     * @throws IllegalStateException if the request is not authenticated with a JWT.
     */
    fun currentToken(): String = currentJwt().tokenValue

    private fun currentJwt(): Jwt {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return principal as? Jwt ?: run {
            log.error("Unable to extract JWT from SecurityContext. Principal type: {}", principal?.javaClass)
            throw IllegalStateException("User must be authenticated with JWT token")
        }
    }

    /** The current user's id (JWT subject) for logging. Never throws. */
    fun currentSubjectOrUnknown(): String =
        runCatching { (SecurityContextHolder.getContext().authentication.principal as Jwt).subject }
            .getOrDefault("unknown")
}
