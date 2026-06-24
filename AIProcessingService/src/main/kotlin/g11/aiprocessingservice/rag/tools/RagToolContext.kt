package g11.aiprocessingservice.rag.tools

import g11.aiprocessingservice.rag.dto.DocumentSource
import org.springframework.ai.chat.model.ToolContext
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Per-request data handed to the RAG tools via Spring AI's [ToolContext] — values placed in
 * `ChatClient.prompt().toolContext(...)` are passed to the `@Tool` methods but **never sent to the
 * model**. That is exactly what we want for the access scope (the model must not be able to widen
 * which files it can see) and for collecting retrieval results out-of-band.
 */
object RagToolContextKeys {
    /** `Set<String>` of fileIds the caller may access (owned + shared); the tool filter is built from this. */
    const val ACCESSIBLE_FILE_IDS = "rag.accessibleFileIds"

    /** `Int` top-K for content search. */
    const val TOP_K = "rag.topK"

    /** [RetrievedSourcesCapture] the search tool appends to, so the caller can read what was retrieved. */
    const val CAPTURE = "rag.capture"
}

/**
 * Collects the chunks the `searchDocumentContent` tool retrieved during a request. With retrieval now
 * happening *inside* a tool call (instead of a mandatory advisor), this is how [RagService] still
 * gets the sources it needs for the UI SOURCES frame and the Langfuse relevance score.
 *
 * Thread-safe: the tool may run on a Reactor thread (streaming) while the caller reads the snapshot
 * on the request/stream thread.
 */
class RetrievedSourcesCapture {
    private val sources = CopyOnWriteArrayList<DocumentSource>()

    fun add(items: List<DocumentSource>) {
        sources.addAll(items)
    }

    fun snapshot(): List<DocumentSource> = sources.toList()
}

/**
 * The resolved view of the tool context, with the unchecked map casts done once in [from].
 * Falls back to safe empties so a malformed context can never throw inside a tool call.
 */
data class RagToolRequest(
    val accessibleFileIds: Set<String>,
    val topK: Int,
    val capture: RetrievedSourcesCapture,
) {
    companion object {
        private const val DEFAULT_TOP_K = 5

        @Suppress("UNCHECKED_CAST")
        fun from(toolContext: ToolContext): RagToolRequest {
            val ctx = toolContext.context
            return RagToolRequest(
                accessibleFileIds = ctx[RagToolContextKeys.ACCESSIBLE_FILE_IDS] as? Set<String> ?: emptySet(),
                topK = ctx[RagToolContextKeys.TOP_K] as? Int ?: DEFAULT_TOP_K,
                capture = ctx[RagToolContextKeys.CAPTURE] as? RetrievedSourcesCapture ?: RetrievedSourcesCapture(),
            )
        }
    }
}
