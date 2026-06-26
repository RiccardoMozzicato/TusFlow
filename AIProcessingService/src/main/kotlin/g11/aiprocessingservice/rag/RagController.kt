package g11.aiprocessingservice.rag

import g11.aiprocessingservice.rag.dto.RagQueryRequest
import g11.aiprocessingservice.rag.dto.RagStreamHandle
import g11.aiprocessingservice.rag.retrieval.RagProperties
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(
    name = "RAG",
    description = "Ask questions over the authenticated user's accessible, indexed documents.",
)
class RagController(
    private val ragService: RagServiceInterface,
    private val userContext: RagUserContext,
    private val ragProperties: RagProperties,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * Streaming variant: returns a streamId immediately and pushes the answer token-by-token over the
     * `/user/queue/rag` STOMP destination (the same realtime channel as job status). The client
     * subscribes to that destination and correlates frames by streamId.
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    @PostMapping("/query/stream")
    @Operation(
        summary = "Start a streamed RAG query",
        description = "Accepts a natural-language question and returns a stream id immediately. The answer is pushed asynchronously to the authenticated user's STOMP queue `/user/queue/rag`; each pushed frame has the `RagStreamEvent` shape. Clients correlate frames using the returned `streamId`.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "Question and optional conversation settings.",
            content = [
                Content(
                    schema = Schema(implementation = RagQueryRequest::class),
                    examples = [
                        ExampleObject(
                            name = "Question over accessible documents",
                            value = """{
  "query": "Riassumi i documenti che parlano del budget del progetto",
  "conversationId": "main-chat",
  "topK": 5
}"""
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(
                responseCode = "202",
                description = "The query was accepted. Subscribe to `/user/queue/rag` for `RagStreamEvent` frames.",
                content = [Content(schema = Schema(implementation = RagStreamHandle::class))]
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid JWT.", content = [Content()]),
            ApiResponse(responseCode = "403", description = "The user does not have one of the required roles.", content = [Content()]),
            ApiResponse(responseCode = "503", description = "RAG streaming is disabled by configuration.", content = [Content()])
        ]
    )
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
