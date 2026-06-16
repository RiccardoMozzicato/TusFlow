package g11.aiprocessingservice.unit.controllers

import g11.aiprocessingservice.controllers.JobControllers
import g11.aiprocessingservice.dto.JobKeyDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.FailedJobServiceInterface
import g11.aiprocessingservice.services.FileAccessService
import g11.aiprocessingservice.services.JobStatsServiceInterface
import g11.aiprocessingservice.services.MetadataProcessor
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

class JobControllersTest {
    private lateinit var jobRepository: JobRepository
    private lateinit var metadataProcessor: MetadataProcessor
    private lateinit var failedJobService: FailedJobServiceInterface
    private lateinit var jobStatsService: JobStatsServiceInterface
    private lateinit var fileAccessService: FileAccessService
    private lateinit var controller: JobControllers

    @BeforeEach
    fun setup() {
        jobRepository = mockk()
        metadataProcessor = mockk()
        failedJobService = mockk()
        jobStatsService = mockk()
        fileAccessService = mockk()
        controller = JobControllers(metadataProcessor, jobRepository, failedJobService, jobStatsService, fileAccessService)

        // The controller reads the caller's JWT from the SecurityContext to resolve permissions.
        val jwt = Jwt.withTokenValue(USER_TOKEN).header("alg", "none").subject("user").build()
        SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(jwt, null)
    }

    @AfterEach
    fun tearDown() {
        SecurityContextHolder.clearContext()
    }

    @Test
    fun `stats delegates to the service with the caller token and no since`() {
        val expected = JobStatsDTO(pending = 34, completedSince = 128, failed = 2)
        every { jobStatsService.getStats(null, USER_TOKEN) } returns expected

        val stats = controller.getJobStats(since = null)

        assertEquals(expected, stats)
        verify { jobStatsService.getStats(null, USER_TOKEN) }
    }

    @Test
    fun `stats forwards the since instant to the service`() {
        val since = Instant.parse("2026-06-04T00:00:00Z")
        val expected = JobStatsDTO(pending = 0, completedSince = 5, failed = 0)
        every { jobStatsService.getStats(since, USER_TOKEN) } returns expected

        val stats = controller.getJobStats(since)

        assertEquals(expected, stats)
        verify { jobStatsService.getStats(since, USER_TOKEN) }
    }

    private fun makeJob(
        jobId: UUID,
        fileId: UUID,
        version: Long,
        status: jobStatus,
        attempts: UInt = 0u,
        payload: Map<String, Any>? = null,
    ) =
        Job(
            jobid = jobId,
            fileid = fileId,
            version = version,
            type = "AI_METADATA",
            attempts = attempts,
            payload = payload,
            status = status,
        )

    @Test
    fun `batch returns latest status per pair and omits pairs without a job`() {
        val fileA = UUID.randomUUID()
        val fileB = UUID.randomUUID()
        // Two jobs for (fileA, v1): the greater jobid wins, mirroring the single lookup.
        val older = UUID(0, 1)
        val newer = UUID(0, 2)
        every { jobRepository.findByFileidIn(any()) } returns listOf(
            makeJob(older, fileA, 1, jobStatus.PENDING),
            makeJob(newer, fileA, 1, jobStatus.COMPLETED),
        )

        val response = controller.getJobStatusBatch(
            listOf(JobKeyDTO(fileA, 1), JobKeyDTO(fileB, 1)),
        )

        val body = response.body!!
        assertEquals(1, body.size) // fileB has no job → omitted
        assertEquals(fileA, body[0].fileId)
        assertEquals(1, body[0].version)
        assertEquals(jobStatus.COMPLETED, body[0].status)
    }

    @Test
    fun `batch returns an empty list for an empty body without querying`() {
        val response = controller.getJobStatusBatch(emptyList())

        assertEquals(emptyList(), response.body)
        verify(exactly = 0) { jobRepository.findByFileidIn(any()) }
    }

    @Test
    fun `batch rejects an oversized request`() {
        val keys = (1..201).map { JobKeyDTO(UUID.randomUUID(), 1) }

        val response = controller.getJobStatusBatch(keys)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(exactly = 0) { jobRepository.findByFileidIn(any()) }
    }

    @Test
    fun `failed list delegates to the service with the requested page`() {
        val dto = FailedJobDTO(
            jobId = UUID(0, 1),
            fileId = UUID.randomUUID(),
            version = 2,
            filename = "report.pdf",
            attempts = 1,
            maxRetries = 4,
            canRetry = true,
            error = "boom",
            failedAt = Instant.parse("2026-06-10T00:00:00Z"),
        )
        val pageable = slot<Pageable>()
        every { failedJobService.listFailedJobs(USER_TOKEN, capture(pageable)) } returns
            PageImpl(listOf(dto))

        val result = controller.listFailedJobs(page = 2, size = 25)

        assertEquals(1, result.content.size)
        assertEquals(dto, result.content[0])
        assertEquals(2, pageable.captured.pageNumber)
        assertEquals(25, pageable.captured.pageSize)
    }

    @Test
    fun `failed list clamps an oversized page size and a negative page`() {
        val pageable = slot<Pageable>()
        every { failedJobService.listFailedJobs(USER_TOKEN, capture(pageable)) } returns PageImpl(emptyList())

        controller.listFailedJobs(page = -5, size = 10_000)

        assertEquals(0, pageable.captured.pageNumber)
        assertEquals(200, pageable.captured.pageSize) // MAX_FAILED_PAGE_SIZE
    }

    @Test
    fun `retry rejects a file the user cannot write and does not start processing`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { fileAccessService.getWritableFileIds(listOf(fileId), USER_TOKEN) } returns emptySet()

        controller.retryFailedJob(fileId, version = 1, res = res, maxUserRetries = 4u)

        verify { res.sendError(HttpServletResponse.SC_FORBIDDEN, any()) }
        verify(exactly = 0) { jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(any(), any()) }
        verify(exactly = 0) { metadataProcessor.retryFailedJobAsync(any()) }
    }

    @Test
    fun `retry at the cap returns 403 and does not start processing`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { fileAccessService.getWritableFileIds(listOf(fileId), USER_TOKEN) } returns setOf(fileId)
        // attempts == cap (4) → blocked: the 4 user retries are spent.
        every {
            jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, 1)
        } returns makeJob(UUID(0, 9), fileId, 1, jobStatus.FAILED, attempts = 4u)

        controller.retryFailedJob(fileId, version = 1, res = res, maxUserRetries = 4u)

        verify { res.sendError(HttpServletResponse.SC_FORBIDDEN, any()) }
        verify(exactly = 0) { metadataProcessor.retryFailedJobAsync(any()) }
    }

    @Test
    fun `retry accepts a writable failed job within the cap`() {
        val fileId = UUID.randomUUID()
        val jobId = UUID(0, 7)
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { fileAccessService.getWritableFileIds(listOf(fileId), USER_TOKEN) } returns setOf(fileId)
        every {
            jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, 1)
        } returns makeJob(jobId, fileId, 1, jobStatus.FAILED, attempts = 1u)
        every { metadataProcessor.retryFailedJobAsync(jobId) } returns CompletableFuture.completedFuture(null)

        controller.retryFailedJob(fileId, version = 1, res = res, maxUserRetries = 4u)

        verify { metadataProcessor.retryFailedJobAsync(jobId) }
        verify { res.status = HttpServletResponse.SC_ACCEPTED }
    }

    @Test
    fun `retry at the last remaining attempt is accepted`() {
        val fileId = UUID.randomUUID()
        val jobId = UUID(0, 8)
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { fileAccessService.getWritableFileIds(listOf(fileId), USER_TOKEN) } returns setOf(fileId)
        // attempts 3 < cap 4 → still allowed (this is the 4th and final user retry).
        every {
            jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, 1)
        } returns makeJob(jobId, fileId, 1, jobStatus.FAILED, attempts = 3u)
        every { metadataProcessor.retryFailedJobAsync(jobId) } returns CompletableFuture.completedFuture(null)

        controller.retryFailedJob(fileId, version = 1, res = res, maxUserRetries = 4u)

        verify { metadataProcessor.retryFailedJobAsync(jobId) }
        verify { res.status = HttpServletResponse.SC_ACCEPTED }
    }

    private companion object {
        const val USER_TOKEN = "user-jwt"
    }
}
