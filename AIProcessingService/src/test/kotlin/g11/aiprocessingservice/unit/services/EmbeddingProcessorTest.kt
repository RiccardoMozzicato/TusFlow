package g11.aiprocessingservice.unit.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.aiprocessingservice.conf.SupportedContentTypeProperties
import g11.aiprocessingservice.dto.FileAnalyzedEvent
import g11.aiprocessingservice.metrics.ExtractionMetrics
import g11.aiprocessingservice.metrics.PipelineMetrics
import g11.aiprocessingservice.services.ChunkingService
import g11.aiprocessingservice.services.DeadLetterService
import g11.aiprocessingservice.services.EmbeddingProcessor
import g11.aiprocessingservice.services.SupportedContentTypes
import g11.aiprocessingservice.services.TextExtractionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID

class EmbeddingProcessorTest {

    private lateinit var textExtractionService: TextExtractionService
    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var deadLetterService: DeadLetterService
    private lateinit var processor: EmbeddingProcessor

    private val supportedContentTypes = SupportedContentTypes(
        SupportedContentTypeProperties(
            prefixes = listOf("text/", "image/"),
            types = listOf("application/pdf"),
        ),
    )

    @BeforeEach
    fun setup() {
        textExtractionService = mockk(relaxed = true)
        kafkaTemplate = mockk(relaxed = true)
        objectMapper = mockk()
        deadLetterService = mockk(relaxed = true)
        processor = EmbeddingProcessor(
            textExtractionService = textExtractionService,
            chunkingService = mockk<ChunkingService>(relaxed = true),
            kafkaTemplate = kafkaTemplate,
            fileServiceClient = mockk(relaxed = true),
            authService = mockk(relaxed = true),
            objectMapper = objectMapper,
            tracer = mockk(relaxed = true),
            supportedContentTypes = supportedContentTypes,
            deadLetterService = deadLetterService,
            pipelineTracing = mockk(relaxed = true),
            // Real metric instances: recordDownload/recordExtraction wrap work in lambdas, so a
            // relaxed mock would skip the block. A SimpleMeterRegistry keeps them in-memory.
            extractionMetrics = ExtractionMetrics(SimpleMeterRegistry()),
            pipelineMetrics = PipelineMetrics(SimpleMeterRegistry()),
        )
    }

    private fun event(contentType: String) = FileAnalyzedEvent(
        jobId = UUID.randomUUID(),
        fileId = UUID.randomUUID(),
        version = 1L,
        fileVersionId = 1L,
        filename = "file",
        contentType = contentType,
        ownerUserId = UUID.randomUUID(),
        summary = "s",
        tags = listOf("t"),
        sensitivity = "PUBLIC",
    )

    @Test
    fun `skips embeddings for an image - no extractable text, no DLT`() {
        every { objectMapper.readValue(any<String>(), FileAnalyzedEvent::class.java) } returns
            event("image/png")

        processor.processFileForEmbeddings("{}", 0L)

        // Returns early: never extracts text, never emits a chunk batch (so nothing can fail/DLT).
        verify(exactly = 0) { textExtractionService.extract(any(), any()) }
        verify(exactly = 0) { kafkaTemplate.send(any<ProducerRecord<String, String>>()) }
    }

    @Test
    fun `handleDlt records the file-analyzed dead letter to the audit service`() {
        val typeBytes = "java.lang.RuntimeException".toByteArray()
        val msgBytes = "extract failed".toByteArray()

        processor.handleDlt("payload-json", typeBytes, msgBytes)

        verify { deadLetterService.record("file-analyzed", "payload-json", typeBytes, msgBytes) }
    }
}
