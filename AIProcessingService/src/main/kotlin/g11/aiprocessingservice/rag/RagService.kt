package g11.aiprocessingservice.rag

import g11.aiprocessingservice.rag.dto.DocumentSource
import g11.aiprocessingservice.rag.dto.RagStreamEvent
import g11.aiprocessingservice.rag.eval.RagScoringService
import g11.aiprocessingservice.rag.memory.ConversationResolver
import g11.aiprocessingservice.rag.retrieval.RagAdvisorFactory
import g11.aiprocessingservice.rag.streaming.RagStreamPublisher
import g11.aiprocessingservice.rag.tools.RagToolContextKeys
import g11.aiprocessingservice.rag.tools.RagTools
import g11.aiprocessingservice.rag.tools.RetrievedSourcesCapture
import g11.aiprocessingservice.services.FileAccessService
import g11.aiprocessingservice.tracing.RagTracing
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.util.UUID

data class RagResponse(
    val answer: String,
    val sourcesCount: Int,
    val sources: List<DocumentSource>,
    val accessibleFilesCount: Int = 0,
)

/**
 * Agentic RAG: the chat model is given two tools ([RagTools]) and decides per question whether to
 * search document contents, list the available documents, both, or neither. Retrieval is no longer a
 * mandatory pre-step — which is what lets the assistant answer catalog/overview questions ("what
 * documents do I have", "summarize everything") from summaries instead of forcing a content search.
 */
@Service
class RagService(
    @Qualifier("ragChatClient") private val chatClient: ChatClient,
    private val advisorFactory: RagAdvisorFactory,
    private val ragTools: RagTools,
    private val fileAccessService: FileAccessService,
    private val userContext: RagUserContext,
    private val conversationResolver: ConversationResolver,
    private val ragTracing: RagTracing,
    private val streamPublisher: RagStreamPublisher,
    private val scoringService: RagScoringService,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    private companion object {
        const val NO_ANSWER = "Nessuna risposta disponibile"
        const val LOG_PREVIEW_CHARS = 90
        // Window size for re-chunking the finished answer into STOMP TOKEN frames (see streamQuery).
        const val STREAM_CHUNK_CHARS = 24
        const val NO_FILES_MESSAGE =
            "Non hai ancora caricato documenti e nessuno ha condiviso documenti con te. " +
                "Carica o richiedi la condivisione di file per utilizzare la ricerca semantica."
    }

    /**
     * Query with RAG across the files the user can access (owned + shared), with conversational memory.
     *
     * The user identity comes from the authenticated JWT (not the request body); [conversationId] is
     * the client-minted conversation key, namespaced per user for isolation.
     *
     * @param userQuery the natural-language query
     * @param conversationId client-minted conversation id (a fresh one is used if null/invalid)
     * @param topK number of top similar chunks the content-search tool retrieves per call
     */
    fun queryWithRag(userQuery: String, conversationId: String?, topK: Int = 5): RagResponse {
        val userId = userContext.currentUserId()
        val userToken = userContext.currentToken()
        val memoryKey = conversationResolver.memoryKey(userId, conversationId)
        log.info("Querying with RAG for user {} (topK={})", userId, topK)

        // Associate the whole trace with this user + conversation (Langfuse session grouping).
        ragTracing.tagTrace(userId.toString(), memoryKey, userQuery)

        val accessibleFileIds = fileAccessService.getAccessibleFileIds(userId, userToken)
        if (accessibleFileIds.isEmpty()) {
            log.warn("User {} has no accessible files (neither owned nor shared)", userId)
            return RagResponse(
                answer = NO_FILES_MESSAGE,
                sourcesCount = 0,
                sources = emptyList(),
                accessibleFilesCount = 0,
            )
        }
        log.info("User {} has access to {} files", userId, accessibleFileIds.size)

        val memoryAdvisor = advisorFactory.memoryAdvisor(memoryKey)
        val capture = RetrievedSourcesCapture()
        val toolContext = buildToolContext(accessibleFileIds, topK, capture)

        // Baggage keeps user/session ids on every child span (tool calls + generation), not just the root.
        return ragTracing.withTraceBaggage(userId.toString(), memoryKey) {
            ragTracing.traceQuery(userQuery, topK) { querySpan ->
                val response = chatClient.prompt()
                    .advisors(memoryAdvisor, SimpleLoggerAdvisor())
                    .tools(ragTools)
                    .toolContext(toolContext)
                    .user(userQuery)
                    .call()
                    .chatClientResponse()

                val answer = response.chatResponse()?.result?.output?.text ?: NO_ANSWER
                // Sources come from whatever the search tool retrieved this turn (empty for a pure
                // catalog/overview answer or small talk).
                val sources = capture.snapshot()
                logRetrievedSources(sources)

                log.info("RAG query completed: {} sources, response length {} chars", sources.size, answer.length)
                querySpan.recordResult(answer, sources.map { it.similarity })

                // Attach evaluation scores to this trace (async, gated, best-effort). Captured inside
                // the span so the trace id matches the query's Langfuse trace.
                scoringService.scoreAsync(ragTracing.currentTraceId(), answer, sources)

                RagResponse(
                    answer = answer,
                    sourcesCount = sources.size,
                    sources = sources,
                    accessibleFilesCount = accessibleFileIds.size,
                )
            }
        }
    }

    /**
     * Streaming variant of [queryWithRag]: pushes a SOURCES frame, then TOKEN frames, then DONE/ERROR
     * to the user's STOMP queue, correlated by [streamId].
     *
     * Runs async so the controller can return immediately. The user identity is resolved on the
     * request thread and passed in, since the SecurityContext is not available on the async thread.
     *
     * Why a blocking `.call()` and not `.stream()`: Spring AI's *streaming* tool-call aggregation is
     * broken for OpenAI-compatible backends that send the tool name only in the first SSE chunk
     * (Qwen et al. — spring-ai#4790), throwing `toolName is null`. The blocking call resolves tools
     * over a non-streamed response (name intact), then we stream the finished answer to the client in
     * chunks. This keeps the SOURCES/TOKEN/DONE contract and works on every backend; the cost is that
     * the answer is generated before the first chunk is sent rather than truly token-by-token.
     */
    @Async
    fun streamQuery(
        userQuery: String,
        conversationId: String?,
        topK: Int,
        userId: UUID,
        userToken: String,
        streamId: String,
    ) {
        try {
            val memoryKey = conversationResolver.memoryKey(userId, conversationId)
            val accessibleFileIds = fileAccessService.getAccessibleFileIds(userId, userToken)
            if (accessibleFileIds.isEmpty()) {
                log.warn("User {} has no accessible files; ending stream {} early", userId, streamId)
                streamPublisher.publish(userId, RagStreamEvent.token(streamId, NO_FILES_MESSAGE))
                streamPublisher.publish(userId, RagStreamEvent.done(streamId))
                return
            }

            val memoryAdvisor = advisorFactory.memoryAdvisor(memoryKey)
            val capture = RetrievedSourcesCapture()
            val toolContext = buildToolContext(accessibleFileIds, topK, capture)

            // Open the "rag-query" root span on this async thread (no ambient request span here) and
            // keep it current across the blocking call so the generation + tool spans nest under it
            // and carry the user/session baggage — same Langfuse grouping as the synchronous path.
            ragTracing.withTraceBaggage(userId.toString(), memoryKey) {
                ragTracing.traceStream(userId.toString(), memoryKey, userQuery) { trace ->
                    try {
                        val response = chatClient.prompt()
                            .advisors(memoryAdvisor, SimpleLoggerAdvisor())
                            .tools(ragTools)
                            .toolContext(toolContext)
                            .user(userQuery)
                            .call()
                            .chatClientResponse()

                        val answer = response.chatResponse()?.result?.output?.text ?: NO_ANSWER
                        // Sources are whatever the search tool retrieved (empty for catalog/small talk).
                        val sources = capture.snapshot()
                        logRetrievedSources(sources)

                        streamPublisher.publish(userId, RagStreamEvent.sources(streamId, sources))
                        publishAnswerInChunks(userId, streamId, answer)
                        trace.recordOutput(answer)
                        streamPublisher.publish(userId, RagStreamEvent.done(streamId))

                        // Best-effort eval scores, captured while the span is current (trace id matches).
                        scoringService.scoreAsync(ragTracing.currentTraceId(), answer, sources)
                    } catch (e: Exception) {
                        log.error("RAG stream {} failed: {}", streamId, e.message, e)
                        streamPublisher.publish(userId, RagStreamEvent.error(streamId, e.message ?: "stream failed"))
                    } finally {
                        trace.end()
                    }
                }
            }
        } catch (e: Exception) {
            log.error("Failed to start RAG stream {}: {}", streamId, e.message, e)
            streamPublisher.publish(userId, RagStreamEvent.error(streamId, e.message ?: "stream setup failed"))
        }
    }

    // Pushes the finished answer to the client as a sequence of TOKEN frames so the UI still renders
    // it progressively. Char-windowed (not word-split) for simplicity — the client concatenates the
    // token text, so the exact answer is reconstructed regardless of where the windows fall.
    private fun publishAnswerInChunks(userId: UUID, streamId: String, answer: String) {
        if (answer.isEmpty()) return
        answer.chunked(STREAM_CHUNK_CHARS).forEach { chunk ->
            streamPublisher.publish(userId, RagStreamEvent.token(streamId, chunk))
        }
    }

    /** The per-request data the tools read off [org.springframework.ai.chat.model.ToolContext]. */
    private fun buildToolContext(
        accessibleFileIds: Set<String>,
        topK: Int,
        capture: RetrievedSourcesCapture,
    ): Map<String, Any> = mapOf(
        RagToolContextKeys.ACCESSIBLE_FILE_IDS to accessibleFileIds,
        RagToolContextKeys.TOP_K to topK,
        RagToolContextKeys.CAPTURE to capture,
    )

    // Logs what the search tool actually returned, so a query that ends in the "not enough
    // information" fallback can be diagnosed from the logs: 0 sources => nothing matched (or the model
    // answered from the catalog / didn't search); sources present but a fallback answer => the chunks
    // didn't contain the answer. INFO carries the summary; the per-chunk detail is DEBUG.
    private fun logRetrievedSources(sources: List<DocumentSource>) {
        if (sources.isEmpty()) {
            log.info("RAG search returned 0 sources -> answer is from the catalog, memory, or a fallback reply")
            return
        }
        log.info(
            "RAG search returned {} sources (similarity {} .. {})",
            sources.size,
            "%.4f".format(sources.minOf { it.similarity }),
            "%.4f".format(sources.maxOf { it.similarity }),
        )
        sources.forEachIndexed { index, source ->
            log.debug(
                "  source[{}] similarity={} file={} preview=\"{}\"",
                index + 1,
                "%.4f".format(source.similarity),
                source.metadata["filename"] ?: source.metadata["fileId"] ?: "unknown",
                source.contentPreview.take(LOG_PREVIEW_CHARS).replace("\n", " "),
            )
        }
    }
}
