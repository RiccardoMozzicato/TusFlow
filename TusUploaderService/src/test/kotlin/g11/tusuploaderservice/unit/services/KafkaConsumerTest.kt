package g11.tusuploaderservice.unit.services

import com.fasterxml.jackson.databind.ObjectMapper
import g11.tusuploaderservice.services.FileServiceInterface
import g11.tusuploaderservice.services.KafkaConsumer
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.propagation.Propagator
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class KafkaConsumerTest {

    private lateinit var fileService: FileServiceInterface
    private lateinit var objectMapper: ObjectMapper
    private lateinit var kafkaConsumer: KafkaConsumer

    @BeforeEach
    fun setUp() {
        fileService = mockk()
        objectMapper = ObjectMapper()
        kafkaConsumer = KafkaConsumer(fileService, objectMapper, mockk(relaxed = true), mockk(relaxed = true))
    }

    @Test
    fun `should process ANALYSIS_COMPLETED event successfully`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"title\":\"Test Document\",\"author\":\"John Doe\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["title"] == "Test Document" &&
                            metadata["author"] == "John Doe"
                }
            )
        }
    }

    @Test
    fun `should process ANALYSIS_UNSUPPORTED event successfully`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 2L
        val message = """
            {
                "event_type": "ANALYSIS_UNSUPPORTED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"error\":\"Unsupported file type\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["error"] == "Unsupported file type"
                }
            )
        }
    }

    @Test
    fun `should process ANALYSIS_DEAD_LETTERED event by persisting the failure on the file`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 3L
        val message = """
            {
                "event_type": "ANALYSIS_DEAD_LETTERED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"status\":\"DEAD_LETTERED\",\"error\":\"boom\",\"metadata\":{}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then: failure metadata persisted on the file. The live browser notification is pushed
        // by AIProcessingService over STOMP, not from this consumer.
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata -> metadata["analysisStatus"] == "FAILED" }
            )
        }
    }

    @Test
    fun `should ignore event with different event_type`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "SOME_OTHER_EVENT",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{}}"
            }
        """.trimIndent()

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }

    @Test
    fun `should process event with empty metadata`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata -> metadata.isEmpty() }
            )
        }
    }

    @Test
    fun `should handle nested JSON objects in metadata`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"nested\":{\"key\":\"value\"},\"simple\":\"text\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["simple"] == "text" &&
                            metadata["nested"]?.contains("key") == true &&
                            metadata["nested"]?.contains("value") == true
                }
            )
        }
    }

    @Test
    fun `should handle arrays in metadata`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"tags\":[\"tag1\",\"tag2\"],\"title\":\"Doc\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["title"] == "Doc" &&
                            metadata["tags"]?.contains("tag1") == true &&
                            metadata["tags"]?.contains("tag2") == true
                }
            )
        }
    }

    @Test
    fun `should handle null metadata field`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata -> metadata.isEmpty() }
            )
        }
    }

    @Test
    fun `should throw exception on invalid JSON`() {
        // Given
        val message = "invalid json"

        // When & Then
        assertThrows<Exception> {
            kafkaConsumer.consumeFileUploadCompleted(message)
        }

        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }

    @Test
    fun `should throw exception on invalid UUID`() {
        // Given
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"invalid-uuid\",\"version\":1,\"metadata\":{}}"
            }
        """.trimIndent()

        // When & Then
        assertThrows<IllegalArgumentException> {
            kafkaConsumer.consumeFileUploadCompleted(message)
        }

        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }

    @Test
    fun `should throw exception when fileService fails`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"title\":\"Test\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(any(), any(), any()) } throws RuntimeException("Database error")

        // When & Then
        assertThrows<RuntimeException> {
            kafkaConsumer.consumeFileUploadCompleted(message)
        }

        verify(exactly = 1) { fileService.updateFileMetadata(fileId, version, any()) }
    }

    @Test
    fun `should process message with multiple metadata fields`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 3L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"title\":\"Document\",\"author\":\"Jane\",\"keywords\":\"test,demo\",\"pages\":\"10\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata.size == 4 &&
                            metadata["title"] == "Document" &&
                            metadata["author"] == "Jane" &&
                            metadata["keywords"] == "test,demo" &&
                            metadata["pages"] == "10"
                }
            )
        }
    }

    @Test
    fun `should handle special characters in metadata values`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "ANALYSIS_COMPLETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"description\":\"Test with \\\"quotes\\\" and \\n newlines\"}}"
            }
        """.trimIndent()

        every { fileService.updateFileMetadata(fileId, version, any()) } just Runs

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 1) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["description"]?.contains("quotes") == true
                }
            )
        }
    }

    @Test
    fun `should ignore FILE_CREATED event type`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "FILE_CREATED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"title\":\"Test\"}}"
            }
        """.trimIndent()

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }

    @Test
    fun `should ignore FILE_DELETED event type`() {
        // Given
        val fileId = UUID.randomUUID()
        val version = 1L
        val message = """
            {
                "event_type": "FILE_DELETED",
                "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{}}"
            }
        """.trimIndent()

        // When
        kafkaConsumer.consumeFileUploadCompleted(message)

        // Then
        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }
}