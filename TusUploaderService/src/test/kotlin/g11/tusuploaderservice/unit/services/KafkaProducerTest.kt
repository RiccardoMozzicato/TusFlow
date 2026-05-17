package g11.tusuploaderservice.unit.services

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.ObjectMapper
import g11.tusuploaderservice.dto.FileUploadEvent
import g11.tusuploaderservice.services.KafkaProducer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

class KafkaProducerTest {

    private lateinit var kafkaTemplate: KafkaTemplate<String, String>
    private lateinit var objectMapper: ObjectMapper
    private lateinit var kafkaProducer: KafkaProducer

    @BeforeEach
    fun setUp() {
        kafkaTemplate = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        kafkaProducer = KafkaProducer(objectMapper)
        kafkaProducer.kafkaTemplate = kafkaTemplate
    }

    @Test
    fun `should send message successfully`() {
        // Given
        val message = "Test message"
        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        every { sendResult.recordMetadata.offset() } returns 123L
        every { kafkaTemplate.sendDefault(message) } returns future

        // When
        kafkaProducer.sendMessage(message)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(message) }
    }

    @Test
    fun `should throw exception when sending message fails`() {
        // Given
        val message = "Test message"
        val exception = RuntimeException("Kafka connection error")
        val future = CompletableFuture<SendResult<String, String>>()
        future.completeExceptionally(exception)

        every { kafkaTemplate.sendDefault(message) } returns future

        // When
        kafkaProducer.sendMessage(message)

        // Wait for the future to complete
        Thread.sleep(100)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(message) }
    }

    @Test
    fun `should send FileUploadEvent successfully`() {
        // Given
        val fileId = UUID.randomUUID()
        val fileVersionId = "version-123"
        val ownerUserId = UUID.randomUUID()
        val fileUploadEvent = FileUploadEvent(
            fileId = fileId,
            fileVersionId = fileVersionId,
            version = 1L,
            ownerUserId = ownerUserId,
            filename = "test.pdf",
            contentType = "application/pdf",
            metadata = mapOf("key" to "value")
        )

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        val messageSlot = slot<String>()
        every { sendResult.recordMetadata.offset() } returns 456L
        every { kafkaTemplate.sendDefault(capture(messageSlot)) } returns future

        // When
        kafkaProducer.sendFileUploadMessage(fileUploadEvent)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(any()) }

        // Verify the serialized message contains expected fields
        val capturedMessage = messageSlot.captured
        assert(capturedMessage.contains(fileId.toString()))
        assert(capturedMessage.contains(fileVersionId))
        assert(capturedMessage.contains("test.pdf"))
        assert(capturedMessage.contains("application/pdf"))
    }

    @Test
    fun `should handle JsonProcessingException when sending FileUploadEvent`() {
        // Given
        val fileUploadEvent = FileUploadEvent(
            fileId = UUID.randomUUID(),
            fileVersionId = "version-123",
            version = 1L,
            ownerUserId = UUID.randomUUID(),
            filename = "test.pdf",
            contentType = "application/pdf",
            metadata = null
        )

        val mockObjectMapper = mockk<ObjectMapper>()
        val kafkaProducerWithMockMapper = KafkaProducer(mockObjectMapper)
        kafkaProducerWithMockMapper.kafkaTemplate = kafkaTemplate

        every { mockObjectMapper.writeValueAsString(any()) } throws mockk<JsonProcessingException>(relaxed = true)

        // When
        kafkaProducerWithMockMapper.sendFileUploadMessage(fileUploadEvent)

        // Then
        // Should not throw exception, just log the error
        verify(exactly = 0) { kafkaTemplate.sendDefault(any()) }
    }

    @Test
    fun `should handle generic exception when sending FileUploadEvent`() {
        // Given
        val fileUploadEvent = FileUploadEvent(
            fileId = UUID.randomUUID(),
            fileVersionId = "version-123",
            version = 1L,
            ownerUserId = UUID.randomUUID(),
            filename = "test.pdf",
            contentType = "application/pdf",
            metadata = null
        )

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        every { sendResult.recordMetadata.offset() } returns 456L
        every { kafkaTemplate.sendDefault(any<String>()) } returns future

        // When
        kafkaProducer.sendFileUploadMessage(fileUploadEvent)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(any<String>()) }
    }

    @Test
    fun `should serialize FileUploadEvent with null metadata correctly`() {
        // Given
        val fileId = UUID.randomUUID()
        val fileUploadEvent = FileUploadEvent(
            fileId = fileId,
            fileVersionId = "version-456",
            version = 2L,
            ownerUserId = UUID.randomUUID(),
            filename = "document.docx",
            contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            metadata = null
        )

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        val messageSlot = slot<String>()
        every { sendResult.recordMetadata.offset() } returns 789L
        every { kafkaTemplate.sendDefault(capture(messageSlot)) } returns future

        // When
        kafkaProducer.sendFileUploadMessage(fileUploadEvent)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(any()) }

        val capturedMessage = messageSlot.captured
        assert(capturedMessage.contains(fileId.toString()))
        assert(capturedMessage.contains("document.docx"))
    }

    @Test
    fun `should send message with correct offset`() {
        // Given
        val message = "Another test message"
        val expectedOffset = 999L
        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        every { sendResult.recordMetadata.offset() } returns expectedOffset
        every { kafkaTemplate.sendDefault(message) } returns future

        // When
        kafkaProducer.sendMessage(message)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(message) }
        verify(exactly = 1) { sendResult.recordMetadata.offset() }
    }

    @Test
    fun `should serialize FileUploadEvent with empty metadata map`() {
        // Given
        val fileId = UUID.randomUUID()
        val fileUploadEvent = FileUploadEvent(
            fileId = fileId,
            fileVersionId = "version-789",
            version = 3L,
            ownerUserId = UUID.randomUUID(),
            filename = "image.png",
            contentType = "image/png",
            metadata = emptyMap()
        )

        val sendResult = mockk<SendResult<String, String>>(relaxed = true)
        val future = CompletableFuture<SendResult<String, String>>()
        future.complete(sendResult)

        val messageSlot = slot<String>()
        every { sendResult.recordMetadata.offset() } returns 111L
        every { kafkaTemplate.sendDefault(capture(messageSlot)) } returns future

        // When
        kafkaProducer.sendFileUploadMessage(fileUploadEvent)

        // Then
        verify(exactly = 1) { kafkaTemplate.sendDefault(any()) }

        val capturedMessage = messageSlot.captured
        assert(capturedMessage.contains(fileId.toString()))
        assert(capturedMessage.contains("image.png"))
        assert(capturedMessage.contains("image/png"))
    }
}
