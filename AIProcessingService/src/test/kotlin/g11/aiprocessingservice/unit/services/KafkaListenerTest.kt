package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.conf.SupportedContentTypeProperties
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.metrics.AnalysisMetrics
import g11.aiprocessingservice.metrics.PipelineMetrics
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.*
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.ai.chat.client.ChatClient
import org.springframework.core.io.Resource
import org.springframework.web.client.RestClient
import org.springframework.http.HttpStatus
import org.springframework.web.client.RestClientResponseException
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class KafkaListenerTest {
    private lateinit var kafkaListener: MetadataProcessor
    private lateinit var jobRepository: JobRepository
    private lateinit var authService: AuthServiceInterface
    private lateinit var fileServiceClient: RestClient
    private lateinit var chatClient: ChatClient
    private lateinit var pdfAnalysisChatClient: ChatClient
    private lateinit var imageChatClient: ChatClient
    private lateinit var jobStatusService: JobStatusServiceInterface
    private lateinit var failedJobService: FailedJobServiceInterface
    private lateinit var promptTemplate: Resource
    private lateinit var kafkaTemplate: org.springframework.kafka.core.KafkaTemplate<String, String>
    private lateinit var tracer: io.micrometer.tracing.Tracer
    private lateinit var propagator: io.micrometer.tracing.propagation.Propagator

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
        imageChatClient = mockk()
        // Relaxed: the processor now also emits non-terminal PENDING/PROCESSING pings
        // (notifyJobPending / notifyJobProcessing) that individual tests don't stub.
        jobStatusService = mockk(relaxed = true)
        failedJobService = mockk(relaxed = true)
        promptTemplate = mockk()
        kafkaTemplate = mockk(relaxed = true)
        tracer = mockk(relaxed = true)
        propagator = mockk(relaxed = true)

        kafkaListener = spyk(buildProcessor(simulateLlmOffline = false), recordPrivateCalls = true)
    }

    private fun buildProcessor(simulateLlmOffline: Boolean, maxAutomaticRetries: Int = 3) =
        MetadataProcessor(
            jobRepository = jobRepository,
            authService = authService,
            fileServiceClient = fileServiceClient,
            chatClient = chatClient,
            pdfAnalysisChatClient = pdfAnalysisChatClient,
            imageChatClient = imageChatClient,
            jobStatusService = jobStatusService,
            kafkaTemplate = kafkaTemplate,
            tracer = mockk(relaxed = true),
            propagator = mockk(relaxed = true),
            promptTemplate = promptTemplate,
            chatModelName = "test-chat-model",
            pdfModelName = "test-pdf-model",
            imageModelName = "test-image-model",
            maxContentChars = 15000,
            beginningPercentage = 70,
            maxAutomaticRetries = maxAutomaticRetries,
            // Zero base delay: the backoff loop runs its full iteration count without any real
            // sleep, so the test exercises the retry logic instantly (no Thread mocking needed).
            analysisBackoffBaseMs = 0,
            analysisBackoffCapMs = 0,
            maxUserRetries = 4,
            simulateLlmOffline = simulateLlmOffline,
            failedJobService = failedJobService,
            textExtractionService = TextExtractionService(),
            supportedContentTypes = SupportedContentTypes(
                SupportedContentTypeProperties(
                    prefixes = listOf("text/", "image/"),
                    types = listOf("application/pdf"),
                ),
            ),
            pipelineTracing = mockk(relaxed = true),
            // Real metric instances (not mocks): recordDownload/recordAnalysis wrap work in lambdas,
            // so a relaxed mock would skip the block. A SimpleMeterRegistry keeps them in-memory.
            analysisMetrics = AnalysisMetrics(SimpleMeterRegistry()),
            pipelineMetrics = PipelineMetrics(SimpleMeterRegistry()),
        )

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
        kafkaListener.onMessage(kafkaMessage, 0L)

        // Assert
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
            attempts = 0u,
            payload = mapOf(),
            status = jobStatus.COMPLETED
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(existingJob)

        // Act
        kafkaListener.onMessage(kafkaMessage, 0L)

        // Assert
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
            attempts = 0u,
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(existingJob)
        every { kafkaListener.processJob(any(), any()) } returns null

        // Act
        kafkaListener.onMessage(kafkaMessage, 0L)

        // Assert
        verify { kafkaListener.processJob(any(), testJobId) }
        verify(exactly = 0) { jobRepository.save(any<Job>()) } // No new job created
    }

    @Test
    fun `onMessage should throw exception when JSON parsing fails`() {
        // Arrange
        val invalidKafkaMessage = "{ invalid json }"

        // Act & Assert
        assertFailsWith<Exception> {
            kafkaListener.onMessage(invalidKafkaMessage, 0L)
        }

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
            kafkaListener.onMessage(kafkaMessage, 0L)
        }

        verify(exactly = 0) { kafkaListener.processJob(any(), any()) }
    }

    @Test
    fun `onMessage should mark job FAILED and rethrow on processing failure`() {
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
            attempts = 0u,
            payload = mapOf(),
            status = jobStatus.PENDING
        )

        every { jobRepository.findById(testJobId) } returns Optional.empty() andThen Optional.of(newJob)
        every { jobRepository.save(any<Job>()) } returns newJob

        // Mock createJobIfNotExists to ensure it returns the job ID
        every { kafkaListener.createJobIfNotExists(any()) } returns testJobId

        every { kafkaListener.processJob(any(), any()) } throws RuntimeException("Processing failed")
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert: the exception propagates so @RetryableTopic can route it to the retry topic/DLT.
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, 0L)
        }

        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Processing failed") }
    }

    @Test
    fun `onMessage should resume and reprocess a previously FAILED job`() {
        // A @RetryableTopic redelivery carries the same file-outbox message for a job we already
        // marked FAILED. createJobIfNotExists must reset it to PENDING and reprocess, instead of
        // skipping it (the old behavior, which silently no-op'd every retry).
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
            attempts = 1u,
            payload = mapOf(),
            status = jobStatus.FAILED // Already failed once
        )

        // Let the real createJobIfNotExists run: it must see FAILED and reset to PENDING.
        every { jobRepository.findById(testJobId) } returns Optional.of(failedJob)
        every { jobRepository.save(any<Job>()) } returns failedJob

        every { kafkaListener.processJob(any(), any()) } throws RuntimeException("Processing failed again")
        every { jobStatusService.markJobAsFailed(any(), any(), any()) } just Runs

        // Act & Assert
        assertFailsWith<RuntimeException> {
            kafkaListener.onMessage(kafkaMessage, 0L)
        }

        // The FAILED job was reset to PENDING (resumed) and reprocessed, then re-failed.
        verify { jobRepository.save(match<Job> { it.status == jobStatus.PENDING }) }
        verify { kafkaListener.processJob(any(), testJobId) }
        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Processing failed again") }
    }

    @Test
    fun `onMessage should skip job that is already PROCESSING`() {
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
            attempts = 0u,
            payload = mapOf(),
            status = jobStatus.PROCESSING
        )

        every { jobRepository.findById(testJobId) } returns Optional.of(processedJob)

        // Act
        kafkaListener.onMessage(kafkaMessage, 0L)

        // Assert: PROCESSING is neither PENDING nor FAILED, so it is skipped (not reprocessed).
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
            attempts = 0u,
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
            kafkaListener.onMessage(kafkaMessage, 0L)
        }

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
            attempts = 0u,
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
            kafkaListener.onMessage(kafkaMessage, 0L)
        }

        verify { jobStatusService.markJobAsFailed(testJobId, any(), "Unknown error") }
    }

    @Test
    fun `processJob marks UNSUPPORTED and skips download for an unsupported content type`() {
        // Arrange: a .dmg-style binary that the allow-list rejects.
        val job = JobDTO(
            id = testJobId,
            fileId = testFileId,
            version = 1L,
            filename = "disk.dmg",
            contentType = "application/x-apple-diskimage",
            ownerUserId = testOwnerUserId,
            fileVersionId = 1L,
        )
        every { jobStatusService.markJobAsUnsupported(any(), any(), any()) } just Runs

        // Act
        val result = kafkaListener.processJob(job, testJobId)

        // Assert: marked UNSUPPORTED, no bytes downloaded, no metadata returned.
        assertNull(result)
        verify { jobStatusService.markJobAsUnsupported(testJobId, job, any()) }
        verify(exactly = 0) { kafkaListener.downloadFile(any(), any()) }
        // An unsupported file short-circuits before analysis, so it never emits a PROCESSING ping.
        verify(exactly = 0) { jobStatusService.notifyJobProcessing(any()) }
    }

    @Test
    fun `onMessage emits a PENDING ping for a queued job before processing`() {
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        every { jobRepository.findById(testJobId) } returns Optional.empty()
        every { jobRepository.save(any<Job>()) } returns mockk()
        every { kafkaListener.processJob(any(), any()) } returns null

        kafkaListener.onMessage(kafkaMessage, 0L)

        // The owner is pinged PENDING so the live indicator turns "in progress" before analysis runs.
        verify { jobStatusService.notifyJobPending(match { it.id == testJobId }) }
    }

    @Test
    fun `processJob emits a PROCESSING ping once analysis starts for a supported type`() {
        val job = JobDTO(
            id = testJobId,
            fileId = testFileId,
            version = 1L,
            filename = "n.txt",
            contentType = "text/plain",
            ownerUserId = testOwnerUserId,
            fileVersionId = 1L,
        )
        // Fresh job; make the download fail fast so the test stays focused on the PROCESSING ping
        // (which fires right after the content-type gate, before the download).
        every { jobRepository.findById(testJobId) } returns Optional.of(
            Job(
                jobid = testJobId, fileid = testFileId, version = 1, type = "FILE_ANALYSIS",
                attempts = 0u, payload = emptyMap(), status = jobStatus.PENDING,
            ),
        )
        every { kafkaListener.downloadFile(testFileId, 1L) } throws RuntimeException("download boom")

        assertFailsWith<RuntimeException> { kafkaListener.processJob(job, testJobId) }

        verify { jobStatusService.notifyJobProcessing(match { it.id == testJobId }) }
    }

    @Test
    fun `handleDlt is log-only and does not throw (terminal dead-lettering happens on user exhaustion)`() {
        // Automatic Kafka exhaustion is NOT terminal for file analysis: the job stays FAILED for the
        // user to retry, so handleDlt only logs — it must not throw and writes no audit row here.
        kafkaListener.handleDlt(
            "payload-json",
            "java.lang.IllegalStateException".toByteArray(),
            "boom".toByteArray(),
        )
    }

    @Test
    fun `onMessage dead-letters the job once the user retry budget is exhausted`() {
        val kafkaMessage = """
            {
                "event_type": "FILE_INSERTED",
                "id": "$testJobId",
                "payload": "{\"id\":\"$testJobId\",\"fileId\":\"$testFileId\",\"version\":1,\"filename\":\"test.txt\",\"contentType\":\"text/plain\",\"ownerUserId\":\"$testOwnerUserId\",\"fileVersionId\":1}"
            }
        """.trimIndent()

        // attempts == cap (maxUserRetries = 4): the user has spent all retries, so the next failure
        // is terminal and must dead-letter rather than mark FAILED.
        val exhaustedJob = Job(
            jobid = testJobId,
            fileid = testFileId,
            version = 1,
            type = "FILE_ANALYSIS",
            attempts = 4u,
            payload = mapOf("error" to "prior failure"),
            status = jobStatus.PENDING,
        )
        every { jobRepository.findById(testJobId) } returns Optional.of(exhaustedJob)
        every { jobRepository.save(any<Job>()) } returns exhaustedJob
        every { kafkaListener.createJobIfNotExists(any()) } returns testJobId
        every { kafkaListener.processJob(any(), any()) } throws RuntimeException("Processing failed")
        every { jobStatusService.markJobAsDeadLettered(any(), any(), any(), any()) } just Runs

        assertFailsWith<RuntimeException> { kafkaListener.onMessage(kafkaMessage, 0L) }

        verify { jobStatusService.markJobAsDeadLettered(testJobId, any(), "Processing failed", any()) }
        verify(exactly = 0) { jobStatusService.markJobAsFailed(any(), any(), any()) }
    }

    @Test
    fun `first analysis runs the full backoff loop, a previously-failed job runs a single attempt`() {
        // The processor is built with analysisBackoffBaseMs = 0, so the backoff loop runs its full
        // iteration count without any real sleep — no need to mock java.lang.Thread (doing so
        // destabilizes the JVM instrumentation agent and hangs the worker).
        // simulateLlmOffline makes every attempt fail before the AI client is touched, so the
        // count of stream.reset() calls (only on attempts after the first) reveals the loop count.
        val processor = spyk(buildProcessor(simulateLlmOffline = true), recordPrivateCalls = true)
        val job = JobDTO(
            id = testJobId,
            fileId = testFileId,
            version = 1L,
            filename = "n.txt",
            contentType = "text/plain",
            ownerUserId = testOwnerUserId,
            fileVersionId = 1L,
        )

        // Fresh upload (no "error" in payload) → 3 attempts → reset() between them (2x).
        val freshStream = mockk<InputStream>(relaxed = true)
        every { freshStream.markSupported() } returns true
        every { processor.downloadFile(testFileId, 1L) } returns freshStream
        every { jobRepository.findById(testJobId) } returns Optional.of(
            Job(
                jobid = testJobId, fileid = testFileId, version = 1, type = "FILE_ANALYSIS",
                attempts = 0u, payload = emptyMap(), status = jobStatus.PENDING,
            ),
        )
        assertFailsWith<RuntimeException> { processor.processJob(job, testJobId) }
        verify(exactly = 2) { freshStream.reset() }

        // Previously failed (payload carries "error") → single immediate attempt → no reset.
        val retryStream = mockk<InputStream>(relaxed = true)
        every { retryStream.markSupported() } returns true
        every { processor.downloadFile(testFileId, 1L) } returns retryStream
        every { jobRepository.findById(testJobId) } returns Optional.of(
            Job(
                jobid = testJobId, fileid = testFileId, version = 1, type = "FILE_ANALYSIS",
                attempts = 1u, payload = mapOf("error" to "prior"), status = jobStatus.PENDING,
            ),
        )
        assertFailsWith<RuntimeException> { processor.processJob(job, testJobId) }
        verify(exactly = 0) { retryStream.reset() }
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
