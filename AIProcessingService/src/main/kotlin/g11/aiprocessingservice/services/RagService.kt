package g11.aiprocessingservice.services

import io.micrometer.tracing.Tracer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.util.UUID

data class RagResponse(
    val answer: String,
    val sourcesCount: Int,
    val sources: List<DocumentSource>,
    val accessibleFilesCount: Int = 0
)

data class DocumentSource(
    val content: String,
    val contentPreview: String,
    val metadata: Map<String, Any>,
    val similarity: Double
)

@Service
class RagService(
    @Qualifier("ragChatClient") private val chatClient: ChatClient,
    private val vectorStore: VectorStore,
    private val fileAccessService: FileAccessService,
    private val tracer: Tracer,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    /**
     * Query with RAG - now includes both owned and shared files
     *
     * @param userQuery The natural language query
     * @param filters Map containing "userId" (the current user's UUID)
     * @param topK Number of top similar chunks to retrieve
     * @return RagResponse with answer and sources
     */
    fun queryWithRag(userQuery: String, filters: Map<String, String>, topK: Int = 5): RagResponse {
        log.info("Querying with RAG: $userQuery (topK=$topK)")

        val userIdString = filters["userId"] ?: filters["ownerId"]
            ?: throw IllegalArgumentException("User ID must be set (use 'userId' parameter)")

        // Tag the active span with Langfuse user and session attributes so that
        // all child spans (vector search, LLM call) are associated with this user.
        tracer.currentSpan()?.let { span ->
            span.tag("langfuse.user.id", userIdString)
            span.tag("langfuse.session.id", UUID.randomUUID().toString())
            span.tag("langfuse.observation.input", userQuery)
            span.tag("langfuse.observation.type","event")
            span.tag("langfuse.environment", "production")
        }
        // langfuse.trace.tags is a string[] — must be set via OTel API directly
        // since Micrometer's span.tag() only supports scalar string values.
        Span.current().setAttribute(
            AttributeKey.stringArrayKey("langfuse.trace.tags"),
            listOf("Prova", "Test", "Ciao")
        )



        val userId = try {
            UUID.fromString(userIdString)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid user ID format: $userIdString")
        }

        // Extract user's JWT token from SecurityContext
        val authentication = SecurityContextHolder.getContext().authentication
        val userToken = when (val principal = authentication?.principal) {
            is Jwt -> principal.tokenValue
            else -> {
                log.error("Unable to extract JWT token from SecurityContext. Principal type: ${principal?.javaClass}")
                throw IllegalStateException("User must be authenticated with JWT token")
            }
        }

        log.debug("Extracted JWT token for user $userId")

        // Get all file IDs accessible by the user (owned + shared)
        val accessibleFileIds = fileAccessService.getAccessibleFileIds(userId, userToken)

        if (accessibleFileIds.isEmpty()) {
            log.warn("User $userId has no accessible files (neither owned nor shared)")
            return RagResponse(
                answer = "Non hai ancora caricato documenti e nessuno ha condiviso documenti con te. Carica o richiedi la condivisione di file per utilizzare la ricerca semantica.",
                sourcesCount = 0,
                sources = emptyList(),
                accessibleFilesCount = 0
            )
        }

        log.info("User $userId has access to ${accessibleFileIds.size} files")

        // Build filter: fileId IN (accessible file IDs)
        val filterExpression = FilterExpressionBuilder()
            .`in`("fileId", *accessibleFileIds.toTypedArray())
            .build()

        val searchRequest = SearchRequest.builder()
            .topK(topK)
            .filterExpression(filterExpression)
            .build()

        // Retrieve documents before passing to chatClient
        val documents = vectorStore.similaritySearch(searchRequest)

        log.info("=== RAG CONTEXT DEBUG ===")
        log.info("Found ${documents.size} document chunks for user $userId (from ${accessibleFileIds.size} accessible files)")

        if (documents.isEmpty()) {
            log.warn("NO DOCUMENTS FOUND! The vector store has no chunks for the user's accessible files.")
            return RagResponse(
                answer = "I tuoi documenti sono ancora in fase di elaborazione. Riprova tra qualche secondo dopo che gli embeddings sono stati generati.",
                sourcesCount = 0,
                sources = emptyList(),
                accessibleFilesCount = accessibleFileIds.size
            )
        }

        documents.forEachIndexed { index, doc ->
            val docContent = doc.text ?: ""
            log.info("""
                |Document #$index:
                |  - Content preview: ${docContent.take(200)}...
                |  - Content length: ${docContent.length} chars
                |  - FileId: ${doc.metadata["fileId"]}
                |  - Filename: ${doc.metadata["filename"]}
                |  - Metadata: ${doc.metadata}
                |  - Similarity score: ${doc.metadata["distance"]}
            """.trimMargin())
        }

        val sources = documents.map { doc ->
            val docContent = doc.text ?: ""
            DocumentSource(
                content = docContent,
                contentPreview = docContent.take(300) + if (docContent.length > 300) "..." else "",
                metadata = doc.metadata,
                similarity = doc.metadata["distance"] as? Double ?: 0.0
            )
        }

        log.info("Passing ${sources.size} document chunks to LLM as context")
        log.info("=== END CONTEXT DEBUG ===")

        val ragQuerySpan = tracer.nextSpan().name("RAG Query").start()
        var result = "Nessuna risposta disponibile"
        tracer.withSpan(ragQuerySpan).use {
            ragQuerySpan.tag("langfuse.observation.input", userQuery)
            ragQuerySpan.tag("rag.topK", topK.toString())

            result = chatClient.prompt()
                .advisors(
                    QuestionAnswerAdvisor.builder(vectorStore)
                        .searchRequest(searchRequest)
                        .build(),
                    SimpleLoggerAdvisor()
                )
                .user(userQuery)
                .call()
                .content() ?: "Nessuna risposta disponibile"

            log.info("RAG query completed. Response length: ${result.length} chars")
            ragQuerySpan.tag("langfuse.observation.output", result)
            ragQuerySpan.end()
        }

        return RagResponse(
            answer = result,
            sourcesCount = sources.size,
            sources = sources,
            accessibleFilesCount = accessibleFileIds.size
        )
    }
}
