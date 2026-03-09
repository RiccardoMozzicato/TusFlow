package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.services.RagService
import g11.aiprocessingservice.services.DocumentSource
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/rag")
class RagController(
    private val ragService: RagService
) {
    private val log= LoggerFactory.getLogger(this::class.java)

    /**
     * Query documents using RAG (Retrieval-Augmented Generation).
     * Searches across both owned files and files shared with the user.
     *
     * @param request RagQueryRequest containing:
     *   - query: Natural language question
     *   - filters: Map with "userId" (user's UUID) - can also accept "ownerId" for backward compatibility
     *   - topK: Number of most similar chunks to retrieve (default: 5)
     * @return Answer with sources and metadata
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    @PostMapping("/query")
    fun query(@RequestBody request: RagQueryRequest): ResponseEntity<RagQueryResponse> {
        log.info("Received RAG query from userId: ${request.filters["userId"] ?: request.filters["ownerId"]}")
        log.info("Query: ${request.query}")

        val ragResponse = ragService.queryWithRag(request.query, request.filters, request.topK ?: 5)

        return ResponseEntity.ok(
            RagQueryResponse(
                query = request.query,
                answer = ragResponse.answer,
                sourcesCount = ragResponse.sourcesCount,
                sources = ragResponse.sources,
                accessibleFilesCount = ragResponse.accessibleFilesCount
            )
        )
    }
}


data class RagQueryRequest(
    val query: String,
    val filters: Map<String, String> = emptyMap(), // Should contain "userId" (or "ownerId" for backward compatibility)
    val topK: Int? = 5
)

data class RagQueryResponse(
    val query: String,
    val answer: String,
    val sourcesCount: Int,
    val sources: List<DocumentSource>,
    val accessibleFilesCount: Int = 0 // Number of files the user has access to (owned + shared)
)



