package g11.aiprocessingservice.rag.tools

import g11.aiprocessingservice.rag.catalog.DocumentCatalogEntry
import g11.aiprocessingservice.rag.catalog.DocumentCatalogRepository
import g11.aiprocessingservice.rag.retrieval.DocumentSearchService
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.model.ToolContext
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Component

/**
 * Both tools are scoped by the caller's accessible fileIds, which arrive via [ToolContext] (see
 * [RagToolContextKeys]) and are therefore **not** model-controllable — the model only supplies the
 * free-text [query]; it can never widen the file scope.
 */
@Component
class RagTools(
    private val searchService: DocumentSearchService,
    private val catalogRepository: DocumentCatalogRepository,
) {
    private val log = LoggerFactory.getLogger(this.javaClass)

    @Tool(
        description = "Search the contents of the user's documents for information relevant to a " +
            "specific question. Use this for any factual question about what a document says or " +
            "contains (e.g. 'what does the contract say about termination?'). Returns the most " +
            "relevant excerpts; base your answer only on them.",
    )
    fun searchDocumentContent(
        @ToolParam(description = "A focused natural-language description of the information to find")
        query: String,
        toolContext: ToolContext,
    ): List<SearchResult> {
        val request = RagToolRequest.from(toolContext)
        val sources = searchService.search(query, request.accessibleFileIds, request.topK)
        // Hand the retrieved chunks back to the caller (for the UI SOURCES frame + relevance scoring).
        request.capture.add(sources)
        log.info("Tool searchDocumentContent(\"{}\") -> {} chunks", query, sources.size)
        return sources.map { SearchResult(it.metadata["filename"]?.toString() ?: "unknown", it.content) }
    }

    @Tool(
        description = "List the documents the user can access, each with a short summary. Use this " +
            "for questions about which documents exist, an overview of all documents, or whether a " +
            "topic is covered anywhere — i.e. when the user is NOT asking about the contents of one " +
            "specific document. Returns one entry per document (filename, summary, tags).",
    )
    fun listAvailableDocuments(toolContext: ToolContext): List<DocumentCatalogEntry> {
        val request = RagToolRequest.from(toolContext)
        val catalog = catalogRepository.findByFileIds(request.accessibleFileIds)
        log.info("Tool listAvailableDocuments -> {} documents", catalog.size)
        return catalog
    }
}

/** Model-facing result of [RagTools.searchDocumentContent]: which file an excerpt came from + its text. */
data class SearchResult(
    val filename: String,
    val content: String,
)
