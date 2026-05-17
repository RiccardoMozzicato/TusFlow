package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.*
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.Resource
import org.springframework.kafka.support.Acknowledgment
import org.springframework.web.client.RestClient
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientResponseException
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class KafkaListenerTest {
    private lateinit var kafkaListener: MetadataProcessor
    private lateinit var jobRepository: JobRepository
    private lateinit var authService: AuthServiceInterface
    private lateinit var fileServiceClient: RestClient
    private lateinit var chatClient: ChatClient
    private lateinit var pdfAnalysisChatClient: ChatClient
    private lateinit var jobStatusService: JobStatusServiceInterface
    private lateinit var promptTemplate: Resource
    private lateinit var acknowledgment: Acknowledgment
    private lateinit var kafkaTemplate: org.springframework.kafka.core.KafkaTemplate<String, String>

    private val testJobId = UUID.randomUUID()
    private val testFileId = UUID.randomUUID()
    private val testOwnerUserId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        jobRepository = mockk()
        authService = mockk()
        fileServiceClient = mockk()
        chatClient = mockk()
        pdfAnalysisChatClient = mockk()
        jobStatusService = mockk()
        promptTemplate = mockk()
        acknowledgment = mockk(relaxed = true)
        kafkaTemplate = mockk(relaxed = true)

        kafkaListener = spyk(MetadataProcessor(
            jobRepository = jobRepository,
            authService = authService,
            fileServiceClient = fileServiceClient,
            chatClient = chatClient,
            pdfAnalysisChatClient = pdfAnalysisChatClient,
            jobStatusService = jobStatusService,
            kafkaTemplate = kafkaTemplate,
            promptTemplate = promptTemplate,
            maxContentChars = 15000,
            beginningPercentage = 70,
        ), recordPrivateCalls = true)
    }

    @Test
    fun `onMessage should successfully process new job`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        // Mock readJson behavior (it's called internally)
        every { jobRepository.findById(testJobId) } returns Optional.empty()
        every { jobRepository.save(any<Job>()) } returns mockk()

        // Mock processJob to do nothing (we test it separately)
        every {
            kafkaListener.processJob(any(), any())
        } returns null

        // Act
        kafkaListener.onMessage(kafkaMessage, acknowledgment)

        // Assert
        verify { acknowledgment.acknowledge() }
        verify { jobRepository.save(any<Job>()) }
        verify { kafkaListener.processJob(any(), testJobId) }
    }

    @Test
    fun `onMessage should skip already completed job`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val existingJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.COMPLETED
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(existingJob)

        // Act
        kafkaListener.onMessage(kafkaMessage, acknowledgment)

        // Assert
        verify { acknowledgment.acknowledge() }
        verify(exactly = 0) { kafkaListener.processJob(any(), any()) }
        verify(exactly = 0) { jobRepository.save(any<Job>()) }
    }

    @Test
    fun `onMessage should resume stuck PENDING job`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val existingJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(existingJob)
        every { kafkaListener.processJob(any(), any()) } returns null

        // Act
        kafkaListener.onMessage(kafkaMessage, acknowledgment)

        // Assert
        verify { acknowledgment.acknowledge() }
        verify { kafkaListener.processJob(any(), testJobId) }
        verify(exactly = 0) { jobRepository.save(any<Job>()) } // No new job created
    }

    @Test
    fun `onMessage should throw exception when JSON parsing fails`() {
        // Arrange
        val invalidKafkaMessage = "{ invalid json }"

        // Act & Assert
        assertFailsWith<Exception> {
            kafkaListener.onMessage(invalidKafkaMessage, acknowledgment)
        }

        verify(exactly = 0) { acknowledgment.acknowledge() }
        verify(exactly = 0) { kafkaListener.processJob(any(), any()) }
    }

    @Test
    fun `onMessage should throw exception when createJobIfNotExists fails`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        every { jobRepository.findById(testJobId) } throws RuntimeException("Database error")

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, acknowledgment)
        }

        verify(exactly = 0) { acknowledgment.acknowledge() }
        verify(exactly = 0) { kafkaListener.processJob(any(), any()) }
    }

    @Test
    fun `onMessage should handle first failure and retry`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val newJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        every { jobRepository.findById(testJobId) } returns Optional.empty() andThen Optional.of(newJob)
        every { jobRepository.save(any<Job>()) } returns newJob

        // Mock createJobIfNotExists to ensure it returns the job ID
        every { kafkaListener.createJobIfNotExists(any()) } returns testJobId

        every { kafkaListener.processJob(any(), any()) } throws RuntimeException("Processing failed")
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, acknowledgment)
        }

        verify { acknowledgment.acknowledge() }
        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Processing failed") }
    }

    @Test
    fun `onMessage should send to DLT on retry failure`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val failedJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.FAILED // Already failed once
        )

        // For a retry scenario, the job already exists with FAILED status
        // createJobIfNotExists finds it and returns the jobId (to resume)
        // Then processJob is called and fails again
        // The exception handler checks the status and sees it's FAILED (retry)
        every { jobRepository.findById(testJobId) } returns Optional.of(failedJob)
        every { jobRepository.save(any<Job>()) } returns failedJob

        // Don't mock createJobIfNotExists - let it run and see that job exists with FAILED status
        // According to the code, if job exists but is not PENDING, it returns null
        // But we need to test the retry scenario, so we need the job to have PENDING status
        // Let's change the job status to PENDING to simulate a stuck retry
        val pendingJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PENDING // Stuck in PENDING from previous attempt
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(pendingJob) andThen Optional.of(failedJob)

        every { kafkaListener.processJob(any(), any()) } throws RuntimeException("Processing failed again")
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, acknowledgment)
        }

        verify { acknowledgment.acknowledge() }
        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Retry failed: Processing failed again") }
    }

    @Test
    fun `onMessage should acknowledge even when job is null`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val processedJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PROCESSING
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(processedJob)

        // Act
        kafkaListener.onMessage(kafkaMessage, acknowledgment)

        // Assert
        verify { acknowledgment.acknowledge() }
        verify(exactly = 0) { kafkaListener.processJob(any(), any()) }
    }

    @Test
    fun `onMessage should handle processJob throwing IllegalStateException`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val newJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        every { jobRepository.findById(testJobId) } returns Optional.empty() andThen Optional.of(newJob)
        every { jobRepository.save(any<Job>()) } returns newJob

        // Mock createJobIfNotExists to ensure it returns the job ID
        every { kafkaListener.createJobIfNotExists(any()) } returns testJobId

        every { kafkaListener.processJob(any(), any()) } throws IllegalStateException("AI returned incomplete metadata")
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert
        assertFailsWith<IllegalStateException> {
            kafkaListener.onMessage(kafkaMessage, acknowledgment)
        }

        verify { acknowledgment.acknowledge() }
        verify { jobStatusService.markJobAsFailed(testJobId, any(), "AI returned incomplete metadata") }
    }

    @Test
    fun `onMessage should handle missing error message gracefully`() {
        // Arrange
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        val newJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        // First call in createJobIfNotExists, second call in exception handler
        every { jobRepository.findById(testJobId) } returns Optional.empty() andThen Optional.of(newJob)
        every { jobRepository.save(any<Job>()) } returns newJob


        // Exception with null message
        val exceptionWithoutMessage = object : RuntimeException() {
            override val message: String? = null
        }
        every { kafkaListener.processJob(any(), any()) } throws exceptionWithoutMessage
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, acknowledgment)
        }

        verify { acknowledgment.acknowledge() }
        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Unknown error") }
    }

    @Test
    fun `downloadFile should call authService to get token`() {
        // Arrange
        val testToken = "valid-bearer-token-12345"
        val mockResource: Resource = mockk(relaxed = true)
        val mockInputStream = ByteArrayInputStream("file content".toByteArray())

        every { authService.getAccessToken() } returns testToken
        every { mockResource.inputStream } returns mockInputStream

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(any()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } returns mockResource

        // Act
        val result = kafkaListener.downloadFile(testFileId, 1L)

        // Assert
        assertNotNull(result)
        verify(exactly = 1) { authService.getAccessToken() }
        verify { requestHeadersUriSpec.headers(any()) }
    }

    @Test
    fun `downloadFile should fail when authService returns null or empty token`() {
        // Arrange
        every { authService.getAccessToken() } returns ""

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(any()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } throws RestClientResponseException("Unauthorized", HttpStatus.UNAUTHORIZED.value(), "Unauthorized", null, null, null)

        // Act & Assert
        val exception = assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            kafkaListener.downloadFile(testFileId, 1L)
        }

        assertEquals(HttpStatus.BAD_GATEWAY, exception.statusCode)
        verify { authService.getAccessToken() }
    }

    @Test
    fun `downloadFile should set Bearer token in request headers`() {
        // Arrange
        val testToken = "secret-token-abc123"
        val mockResource: Resource = mockk(relaxed = true)
        val mockInputStream = ByteArrayInputStream("file content".toByteArray())

        every { authService.getAccessToken() } returns testToken
        every { mockResource.inputStream } returns mockInputStream

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)
        val headersSlot = slot<java.util.function.Consumer<org.springframework.http.HttpHeaders>>()

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(capture(headersSlot)) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } returns mockResource

        // Act
        kafkaListener.downloadFile(testFileId, 1L)

        // Assert - Verify that the captured headers consumer sets Bearer auth
        verify { authService.getAccessToken() }
        verify { requestHeadersUriSpec.headers(any()) }

        // Verify the headers consumer was called with setBearerAuth
        val mockHeaders: org.springframework.http.HttpHeaders = mockk(relaxed = true)
        headersSlot.captured.accept(mockHeaders)
        verify { mockHeaders.setBearerAuth(testToken) }
    }

    @Test
    fun `downloadFile should throw exception when token is invalid (401 response)`() {
        // Arrange
        val invalidToken = "invalid-token"

        every { authService.getAccessToken() } returns invalidToken

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(any()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } throws RestClientResponseException(
            "Unauthorized",
            HttpStatus.UNAUTHORIZED.value(),
            "Unauthorized",
            null,
            null,
            null
        )

        // Act & Assert - downloadFile converte RestClientResponseException in ResponseStatusException con BAD_GATEWAY
        val exception = assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            kafkaListener.downloadFile(testFileId, 1L)
        }

        assertEquals(HttpStatus.BAD_GATEWAY, exception.statusCode)
        verify { authService.getAccessToken() }
    }

    @Test
    fun `downloadFile should throw exception when token is missing (403 response)`() {
        // Arrange
        every { authService.getAccessToken() } returns ""

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(any()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } throws RestClientResponseException(
            "Forbidden",
            HttpStatus.FORBIDDEN.value(),
            "Forbidden",
            null,
            null,
            null
        )

        // Act & Assert - downloadFile converte RestClientResponseException in ResponseStatusException con BAD_GATEWAY
        val exception = assertFailsWith<org.springframework.web.server.ResponseStatusException> {
            kafkaListener.downloadFile(testFileId, 1L)
        }

        assertEquals(HttpStatus.BAD_GATEWAY, exception.statusCode)
        verify { authService.getAccessToken() }
    }

    @Test
    fun `downloadFile should not call fileServiceClient when authService fails`() {
        // Arrange
        every { authService.getAccessToken() } throws RuntimeException("Auth service unavailable")

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.downloadFile(testFileId, 1L)
        }

        verify { authService.getAccessToken() }
        verify(exactly = 0) { fileServiceClient.get() }
    }

    @Test
    fun `downloadFile should successfully download with valid token`() {
        // Arrange
        val validToken = "valid-jwt-token-xyz"
        val expectedFileContent = "This is the file content"
        val mockResource: Resource = mockk(relaxed = true)
        val mockInputStream = ByteArrayInputStream(expectedFileContent.toByteArray())

        every { authService.getAccessToken() } returns validToken
        every { mockResource.inputStream } returns mockInputStream

        val requestHeadersUriSpec: RestClient.RequestHeadersUriSpec<*> = mockk(relaxed = true)
        val responseSpec: RestClient.ResponseSpec = mockk(relaxed = true)

        every { fileServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<java.util.function.Function<org.springframework.web.util.UriBuilder, java.net.URI>>()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.headers(any()) } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.retrieve() } returns responseSpec
        every { responseSpec.body(Resource::class.java) } returns mockResource

        // Act
        val result = kafkaListener.downloadFile(testFileId, 1L)

        // Assert
        assertNotNull(result)
        val content = result!!.bufferedReader().use { it.readText() }
        assertEquals(expectedFileContent, content)
        verify(exactly = 1) { authService.getAccessToken() }
        verify(exactly = 1) { fileServiceClient.get() }
    }
}
