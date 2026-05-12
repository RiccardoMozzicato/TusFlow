package g11.tusuploaderservice.integration.services

import com.ninjasquad.springmockk.MockkBean
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.StorageService
import io.mockk.every
import io.mockk.verify
import io.mockk.clearMocks
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.TestPropertySource
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.test.Test

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
    partitions = 1,
    topics = ["job-outbox"],
    brokerProperties = ["listeners=PLAINTEXT://localhost:9094", "port=9094"]
)
@TestPropertySource(properties = [
    "spring.kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.group-id=test-group-tus",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "spring.kafka.consumer.enable-auto-commit=true",
    "spring.kafka.producer.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "kafka.bootstrap-servers=\${spring.embedded.kafka.brokers}",
    "spring.datasource.url=jdbc:h2:mem:testdb",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "minio.url=http://localhost:9000",
    "minio.access-key=minioadmin",
    "minio.secret-key=minioadmin",
    "spring.docker.compose.enabled=false",
    "spring.task.scheduling.enabled=false"
])
class KafkaConsumerIntegrationTest {
    @MockkBean
    private lateinit var fileService: FileService

    @MockkBean
    private lateinit var storageService: StorageService

    private lateinit var producer: KafkaProducer<String, String>

    @BeforeEach
    fun setup() {
        setupKafka()
        setupMocks()
    }

    fun setupKafka() {
        val producerProps = mapOf(
            "bootstrap.servers" to "localhost:9094",
            "key.serializer" to StringSerializer::class.java.name,
            "value.serializer" to StringSerializer::class.java.name,
            "acks" to "all",
            "retries" to "3"
        )
        producer = KafkaProducer(producerProps)
        println("✓ Kafka producer configured for localhost:9094")
    }

    fun setupMocks() {
        clearMocks(fileService)
        every { fileService.updateFileMetadata(any(), any(), any()) } returns Unit
    }

    @AfterEach
    fun cleanup() {
        try {
            producer.close()
        } catch (e: Exception) {
            println("Error closing Kafka producer: ${e.message}")
        }
    }

    @Test
    fun `consume message successfully 1`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"key\":\"value\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        // Wait for the consumer to process the message
        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) { fileService.updateFileMetadata(fileId, version, any()) }
    }

    @Test
    fun `consume message successfully 2`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_UNSUPPORTED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"key\":\"value\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        // Wait for the consumer to process the message
        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) { fileService.updateFileMetadata(fileId, version, any()) }
    }

    @Test
    fun `should ignore message with different event type`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "OTHER_EVENT",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"key\":\"value\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        // Wait to ensure message is processed (or ignored)
        Thread.sleep(3000)

        // Assert - fileService should NOT be called
        verify(exactly = 0) { fileService.updateFileMetadata(any(), any(), any()) }
    }

    @Test
    fun `should process message with complex nested metadata`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 2L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"tags\":[\"document\",\"pdf\"],\"author\":{\"name\":\"John Doe\",\"email\":\"john@example.com\"},\"size\":\"1024\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata.containsKey("tags") &&
                    metadata.containsKey("author") &&
                    metadata.containsKey("size")
                }
            )
        }
    }

    @Test
    fun `should process message with empty metadata`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(fileId, version, emptyMap())
        }
    }

    @Test
    fun `should process message with multiple metadata fields`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 3L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"title\":\"Document Title\",\"category\":\"Finance\",\"confidence\":\"0.95\",\"language\":\"en\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata.size == 4 &&
                    metadata["title"] == "Document Title" &&
                    metadata["category"] == "Finance" &&
                    metadata["confidence"] == "0.95" &&
                    metadata["language"] == "en"
                }
            )
        }
    }

    @Test
    fun `should handle message with array metadata`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"keywords\":[\"finance\",\"report\",\"2024\"],\"detected_entities\":[\"Organization\",\"Location\"]}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata.containsKey("keywords") &&
                    metadata.containsKey("detected_entities")
                }
            )
        }
    }

    @Test
    fun `should process ANALYSIS_UNSUPPORTED with error metadata`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_UNSUPPORTED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"error\":\"Unsupported file format\",\"file_type\":\"unknown\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["error"] == "Unsupported file format" &&
                    metadata["file_type"] == "unknown"
                }
            )
        }
    }

    @Test
    fun `should handle message with special characters in metadata`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 1L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"description\":\"Test with special chars: àèéìòù €\",\"path\":\"/documents/test.pdf\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(fileId, version, any())
        }
    }

    @Test
    fun `should process message with numeric metadata values`() {
        // Arrange
        val fileId = UUID.randomUUID()
        val version = 5L

        val message = """
        {
            "event_type": "ANALYSIS_COMPLETED",
            "payload": "{\"fileid\":\"$fileId\",\"version\":$version,\"metadata\":{\"page_count\":\"25\",\"word_count\":\"5432\",\"confidence_score\":\"0.987\"}}"
        }
        """.trimIndent()

        // Act
        val record = ProducerRecord<String, String>("job-outbox", message)
        producer.send(record).get(10, TimeUnit.SECONDS)

        Thread.sleep(3000)

        // Assert
        verify(timeout = 5000) {
            fileService.updateFileMetadata(
                fileId,
                version,
                match { metadata ->
                    metadata["page_count"] == "25" &&
                    metadata["word_count"] == "5432" &&
                    metadata["confidence_score"] == "0.987"
                }
            )
        }
    }

}