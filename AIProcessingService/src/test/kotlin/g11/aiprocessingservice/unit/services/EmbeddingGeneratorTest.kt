package g11.aiprocessingservice.unit.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.ChunkBatchEvent
import g11.aiprocessingservice.dto.ChunkData
import g11.aiprocessingservice.entities.FileVersionEmbedding
import g11.aiprocessingservice.repositories.FileVersionEmbeddingRepository
import g11.aiprocessingservice.services.DeadLetterService
import g11.aiprocessingservice.services.EmbeddingGenerator
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.ai.document.Document
import org.springframework.ai.vectorstore.VectorStore

/**
 * Unit-tests the retry routing of [EmbeddingGenerator.generateEmbeddings]: any chunk failure rethrows
 * so @RetryableTopic redelivers the whole batch, while chunks already embedded are skipped (deduped)
 * rather than re-embedded.
 */
class EmbeddingGeneratorTest {

    private val objectMapper = jacksonObjectMapper()
    private lateinit var vectorStore: VectorStore
    private lateinit var embeddingRepository: FileVersionEmbeddingRepository
    private lateinit var deadLetterService: DeadLetterService
    private lateinit var generator: EmbeddingGenerator

    @BeforeEach
    fun setup() {
        vectorStore = mockk(relaxed = true)
        embeddingRepository = mockk(relaxed = true)
        deadLetterService = mockk(relaxed = true)

        // No chunk exists yet by default → every chunk is a fresh embed attempt.
        every { embeddingRepository.findByFileVersionIdAndChunkIndex(any(), any()) } returns null
        every { embeddingRepository.save(any<FileVersionEmbedding>()) } answers { firstArg() }

        generator = EmbeddingGenerator(
            vectorStore = vectorStore,
            embeddingRepository = embeddingRepository,
            objectMapper = objectMapper,
            deadLetterService = deadLetterService,
            pipelineTracing = mockk(relaxed = true),
            embeddingModelName = "test-model",
            embeddingDimensions = "8",
        )
    }

    private fun chunk(index: Int) = ChunkData(chunkIndex = index, content = "c$index", metadata = emptyMap())

    private fun batch(vararg chunks: ChunkData) = objectMapper.writeValueAsString(
        ChunkBatchEvent(
            fileVersionId = 1L,
            batchNumber = 0,
            totalBatches = 1,
            chunks = chunks.toList(),
        ),
    )

    /** Make vectorStore.add throw only for the chunk whose content matches [failingContent]. */
    private fun failAddFor(failingContent: String) {
        every { vectorStore.add(match<List<Document>> { it.first().text == failingContent }) } throws
            RuntimeException("embedding backend down")
    }

    @Test
    fun `partial failure throws so RetryableTopic redelivers the batch, successes are persisted`() {
        failAddFor("c1")  // chunk 0 succeeds, chunk 1 fails

        assertThrows<IllegalStateException> {
            generator.generateEmbeddings(batch(chunk(0), chunk(1)))
        }

        // The chunk that succeeded was persisted, so the redelivery will skip it.
        verify { embeddingRepository.save(match<FileVersionEmbedding> { it.chunkIndex == 0 }) }
    }

    @Test
    fun `whole batch fails - throws`() {
        failAddFor("c0")
        failAddFor("c1")

        assertThrows<IllegalStateException> {
            generator.generateEmbeddings(batch(chunk(0), chunk(1)))
        }
    }

    @Test
    fun `fully successful batch - does not throw`() {
        generator.generateEmbeddings(batch(chunk(0), chunk(1)))

        verify(exactly = 2) { embeddingRepository.save(any<FileVersionEmbedding>()) }
    }

    @Test
    fun `already-embedded chunk is skipped and never re-embedded`() {
        every { embeddingRepository.findByFileVersionIdAndChunkIndex(1L, 0) } returns
            mockk<FileVersionEmbedding>()

        generator.generateEmbeddings(batch(chunk(0)))

        verify(exactly = 0) { vectorStore.add(any()) }
        verify(exactly = 0) { embeddingRepository.save(any<FileVersionEmbedding>()) }
    }

    @Test
    fun `handleDlt records the embeddings-batch dead letter to the audit service`() {
        val typeBytes = "java.lang.RuntimeException".toByteArray()
        val msgBytes = "all chunks failed".toByteArray()

        generator.handleDlt("payload-json", typeBytes, msgBytes)

        verify { deadLetterService.record("embeddings-batch", "payload-json", typeBytes, msgBytes) }
    }
}
