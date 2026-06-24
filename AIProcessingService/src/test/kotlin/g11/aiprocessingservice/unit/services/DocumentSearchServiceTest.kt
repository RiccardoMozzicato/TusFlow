package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.rag.retrieval.DocumentSearchService
import g11.aiprocessingservice.rag.retrieval.RagProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.SearchRequest
import org.springframework.ai.vectorstore.VectorStore

class DocumentSearchServiceTest {

    private val vectorStore: VectorStore = mockk()
    private val service = DocumentSearchService(vectorStore, RagProperties())

    @Test
    fun `maps cosine distance to similarity and previews content`() {
        val doc = Document.builder()
            .text("chunk text")
            .metadata(mapOf("fileId" to "f1", "filename" to "doc.pdf", "distance" to 0.2))
            .build()
        every { vectorStore.similaritySearch(any<SearchRequest>()) } returns listOf(doc)

        val sources = service.search("q", setOf("f1"), topK = 5)

        assertEquals(1, sources.size)
        // distance 0.2 -> cosine similarity 0.8
        assertEquals(0.8, sources[0].similarity, 1e-9)
        assertEquals("chunk text", sources[0].content)
    }

    @Test
    fun `returns empty and never hits the vector store when the user has no accessible files`() {
        val sources = service.search("q", emptySet(), topK = 5)

        assertTrue(sources.isEmpty())
        verify(exactly = 0) { vectorStore.similaritySearch(any<SearchRequest>()) }
    }
}
