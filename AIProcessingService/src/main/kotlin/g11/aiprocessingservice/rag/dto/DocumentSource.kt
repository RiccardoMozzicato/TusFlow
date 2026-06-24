package g11.aiprocessingservice.rag.dto

/**
 * One retrieved chunk backing a RAG answer.
 *
 * @property similarity cosine similarity in [0,1] (1 = most similar), derived from the vector
 *   store's cosine *distance* (`similarity = 1 - distance`).
 */
data class DocumentSource(
    val content: String,
    val contentPreview: String,
    val metadata: Map<String, Any>,
    val similarity: Double,
)
