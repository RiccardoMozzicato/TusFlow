package g11.aiprocessingservice.unit.controllers

import g11.aiprocessingservice.controllers.JobControllers
import g11.aiprocessingservice.dto.JobKeyDTO
import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.services.FailedJobServiceInterface
import g11.aiprocessingservice.services.JobStatsServiceInterface
import g11.aiprocessingservice.services.JobStatusServiceInterface
import g11.aiprocessingservice.services.RetryOutcome
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
import kotlin.test.assertEquals

class JobControllersTest {
    private lateinit var failedJobService: FailedJobServiceInterface
    private lateinit var jobStatsService: JobStatsServiceInterface
    private lateinit var jobStatusService: JobStatusServiceInterface
    private lateinit var controller: JobControllers

    @BeforeEach
    fun setup() {
        failedJobService = mockk()
        jobStatsService = mockk()
        jobStatusService = mockk()
        controller = JobControllers(failedJobService, jobStatsService, jobStatusService)

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

    @Test
    fun `batch delegates to the service and returns its result`() {
        val fileA = UUID.randomUUID()
        val keys = listOf(JobKeyDTO(fileA, 1))
        val expected = listOf(
            JobStatusResponseDTO(
                jobId = UUID(0, 2),
                fileId = fileA,
                version = 1,
                status = jobStatus.COMPLETED,
            ),
        )
        every { jobStatusService.getJobStatusBatch(keys) } returns expected

        val response = controller.getJobStatusBatch(keys)

        assertEquals(expected, response.body)
        verify { jobStatusService.getJobStatusBatch(keys) }
    }

    @Test
    fun `batch rejects an oversized request without calling the service`() {
        val keys = (1..201).map { JobKeyDTO(UUID.randomUUID(), 1) }

        val response = controller.getJobStatusBatch(keys)

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        verify(exactly = 0) { jobStatusService.getJobStatusBatch(any()) }
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
    fun `retry maps NO_WRITE_ACCESS to 403`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { failedJobService.retryFailedJob(fileId, 1, USER_TOKEN) } returns RetryOutcome.NO_WRITE_ACCESS

        controller.retryFailedJob(fileId, version = 1, res = res)

        verify { res.sendError(HttpServletResponse.SC_FORBIDDEN, any()) }
    }

    @Test
    fun `retry maps JOB_NOT_FOUND to 404`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { failedJobService.retryFailedJob(fileId, 1, USER_TOKEN) } returns RetryOutcome.JOB_NOT_FOUND

        controller.retryFailedJob(fileId, version = 1, res = res)

        verify { res.sendError(HttpServletResponse.SC_NOT_FOUND, any()) }
    }

    @Test
    fun `retry maps MAX_RETRIES_REACHED to 403`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { failedJobService.retryFailedJob(fileId, 1, USER_TOKEN) } returns RetryOutcome.MAX_RETRIES_REACHED

        controller.retryFailedJob(fileId, version = 1, res = res)

        verify { res.sendError(HttpServletResponse.SC_FORBIDDEN, any()) }
    }

    @Test
    fun `retry maps NOT_FAILED to 409`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { failedJobService.retryFailedJob(fileId, 1, USER_TOKEN) } returns RetryOutcome.NOT_FAILED

        controller.retryFailedJob(fileId, version = 1, res = res)

        verify { res.sendError(HttpServletResponse.SC_CONFLICT, any()) }
    }

    @Test
    fun `retry maps ACCEPTED to 202`() {
        val fileId = UUID.randomUUID()
        val res = mockk<HttpServletResponse>(relaxed = true)
        every { failedJobService.retryFailedJob(fileId, 1, USER_TOKEN) } returns RetryOutcome.ACCEPTED

        controller.retryFailedJob(fileId, version = 1, res = res)

        verify { res.status = HttpServletResponse.SC_ACCEPTED }
    }

    private companion object {
        const val USER_TOKEN = "user-jwt"
    }
}
