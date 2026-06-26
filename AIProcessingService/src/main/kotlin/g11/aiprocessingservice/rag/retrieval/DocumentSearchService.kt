package g11.aiprocessingservice.rag.retrieval

import g11.aiprocessingservice.rag.dto.DocumentSource
import org.springframework.ai.document.Document
import org.springframework.ai.rag.Query
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever
import org.springframework.ai.vectorstore.VectorStore
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder
import org.springframework.stereotype.Service

/**
 * Semantic content search over the user's documents, scoped to the files they can access.
 *
 * In the agentic RAG flow this is invoked on demand by the `searchDocumentContent` tool (the model
 * decides *whether* to search and *what* to search for), rather than being a mandatory pre-step.
 * Retrieval itself is the same top-K cosine search as before: [VectorStoreDocumentRetriever] with a
 * `fileId IN (...)` filter so a user only ever sees chunks of their own/shared files.
 */
@Service
class DocumentSearchService(
    private val vectorStore: VectorStore,
    private val props: RagProperties,
) {
    private companion object {
        const val PREVIEW_CHARS = 300
    }

    /**
     * Retrieve the top-[topK] chunks most similar to [query] among [accessibleFileIds].
     *
     * @return the matched chunks as [DocumentSource]s (with cosine similarity); empty when the user
     *   has no accessible files or nothing clears the similarity threshold.
     */
    fun search(query: String, accessibleFileIds: Set<String>, topK: Int): List<DocumentSource> {
        if (accessibleFileIds.isEmpty()) return emptyList()

        val fileFilter = FilterExpressionBuilder()
            .`in`("fileId", *accessibleFileIds.toTypedArray())
            .build()

        val retriever = VectorStoreDocumentRetriever.builder()
            .vectorStore(vectorStore)
            .similarityThreshold(props.retrieval.similarityThreshold)
            .topK(topK)
            .filterExpression(fileFilter)
            .build()

        return retriever.retrieve(Query(query)).map { it.toDocumentSource() }
    }

    private fun Document.toDocumentSource(): DocumentSource {
        val content = text ?: ""
        return DocumentSource(
            content = content,
            contentPreview = content.take(PREVIEW_CHARS) +
                if (content.length > PREVIEW_CHARS) "..." else "",
            metadata = metadata,
            similarity = toSimilarity(metadata["distance"]),
        )
    }

    // pgvector cosine search stores the cosine *distance* under "distance"; similarity = 1 - distance.
    private fun toSimilarity(distance: Any?): Double {
        val d = (distance as? Number)?.toDouble() ?: return 0.0
        return (1.0 - d).coerceIn(0.0, 1.0)
    }
}
