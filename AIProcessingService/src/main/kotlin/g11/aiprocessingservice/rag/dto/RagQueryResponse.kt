package g11.aiprocessingservice.rag.dto

/**
 * RAG query response.
 *
 * @property accessibleFilesCount number of files the user can access (owned + shared)
 */
data class RagQueryResponse(
    val query: String,
    val answer: String,
    val sourcesCount: Int,
    val sources: List<DocumentSource>,
    val accessibleFilesCount: Int = 0,
)
