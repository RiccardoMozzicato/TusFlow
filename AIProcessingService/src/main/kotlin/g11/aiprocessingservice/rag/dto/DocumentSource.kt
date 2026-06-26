package g11.aiprocessingservice.rag.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * One retrieved chunk backing a RAG answer.
 *
 * @property similarity cosine similarity in [0,1] (1 = most similar), derived from the vector
 *   store's cosine *distance* (`similarity = 1 - distance`).
 */
@Schema(description = "Document chunk retrieved as evidence for a RAG answer.")
data class DocumentSource(
    @Schema(description = "Full retrieved chunk text.")
    val content: String,
    @Schema(description = "Short preview displayed by the client.", example = "The 2026 project budget allocates...")
    val contentPreview: String,
    @Schema(
        description = "Chunk metadata written during embedding generation.",
        example = """{"fileId":"1bbba75b-35bb-4e88-9200-df55387c1570","filename":"budget.pdf","version":"2","chunkIndex":"4"}"""
    )
    val metadata: Map<String, Any>,
    @Schema(description = "Cosine similarity in [0,1], where 1 is most similar.", example = "0.84")
    val similarity: Double,
)
