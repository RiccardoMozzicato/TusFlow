package g11.aiprocessingservice.rag

import g11.aiprocessingservice.rag.dto.RagQueryRequest
import g11.aiprocessingservice.rag.dto.RagQueryResponse
import g11.aiprocessingservice.rag.dto.RagStreamHandle
import g11.aiprocessingservice.rag.retrieval.RagProperties
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/rag")
class RagController(
    private val ragService: RagService,
    private val userContext: RagUserContext,
    private val ragProperties: RagProperties,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Query documents using RAG across both owned files and files shared with the user.
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    @PostMapping("/query")
    fun query(@RequestBody request: RagQueryRequest): ResponseEntity<RagQueryResponse> {
        log.debug(
            "API POST /api/rag/query user={} conversationId={} query={}",
            userContext.currentSubjectOrUnknown(),
            request.conversationId,
            request.query,
        )

        val ragResponse = ragService.queryWithRag(request.query, request.conversationId, request.topK ?: 5)

        return ResponseEntity.ok(
            RagQueryResponse(
                query = request.query,
                answer = ragResponse.answer,
                sourcesCount = ragResponse.sourcesCount,
                sources = ragResponse.sources,
                accessibleFilesCount = ragResponse.accessibleFilesCount,
            )
        )
    }

    /**
     * Streaming variant: returns a streamId immediately and pushes the answer token-by-token over the
     * `/user/queue/rag` STOMP destination (the same realtime channel as job status). The client
     * subscribes to that destination and correlates frames by streamId.
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    @PostMapping("/query/stream")
    fun queryStream(@RequestBody request: RagQueryRequest): ResponseEntity<RagStreamHandle> {
        if (!ragProperties.streaming.enabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        }

        // Resolve identity on the request thread; the async stream can't read the SecurityContext.
        val userId = userContext.currentUserId()
        val userToken = userContext.currentToken()
        val streamId = UUID.randomUUID().toString()

        log.debug(
            "API POST /api/rag/query/stream user={} conversationId={} streamId={}",
            userId, request.conversationId, streamId,
        )

        ragService.streamQuery(
            userQuery = request.query,
            conversationId = request.conversationId,
            topK = request.topK ?: ragProperties.retrieval.topK,
            userId = userId,
            userToken = userToken,
            streamId = streamId,
        )

        return ResponseEntity.accepted().body(RagStreamHandle(streamId))
    }
}
