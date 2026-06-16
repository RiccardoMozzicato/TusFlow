package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.FailedJobService
import g11.aiprocessingservice.services.FileAccessService
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FailedJobServiceTest {
    private lateinit var jobRepository: JobRepository
    private lateinit var fileAccessService: FileAccessService
    private lateinit var service: FailedJobService

    private val token = "user-jwt"

    @BeforeEach
    fun setup() {
        jobRepository = mockk()
        fileAccessService = mockk()
        service = FailedJobService(jobRepository, fileAccessService, maxUserRetries = 4)
    }

    private fun makeJob(
        jobId: UUID,
        fileId: UUID,
        version: Long,
        attempts: UInt = 0u,
        payload: Map<String, Any>? = null,
    ) = Job(
        jobid = jobId,
        fileid = fileId,
        version = version,
        type = "AI_METADATA",
        attempts = attempts,
        payload = payload,
        status = jobStatus.FAILED,
    )

    @Test
    fun `maps payload fields and flags retry eligibility against the cap`() {
        val fileId = UUID.randomUUID()
        val otherFileId = UUID.randomUUID()
        val failedFileIds = listOf(fileId, otherFileId)
        val writable = setOf(fileId, otherFileId)
        val retryable = makeJob(
            jobId = UUID(0, 1),
            fileId = fileId,
            version = 2,
            attempts = 1u,
            payload = mapOf("filename" to "report.pdf", "error" to "boom"),
        )
        val exhausted = makeJob(
            jobId = UUID(0, 2),
            fileId = otherFileId,
            version = 1,
            attempts = 4u,
            payload = null,
        )
        every { jobRepository.findDistinctFileidByStatus(jobStatus.FAILED) } returns failedFileIds
        every { fileAccessService.getWritableFileIds(failedFileIds, token) } returns writable
        every {
            jobRepository.findByStatusAndFileidInOrderByUpdatedAtDesc(jobStatus.FAILED, writable, any())
        } returns PageImpl(listOf(retryable, exhausted))

        val result = service.listFailedJobs(token, PageRequest.of(0, 100)).content

        assertEquals(2, result.size)
        assertEquals(fileId, result[0].fileId)
        assertEquals(2, result[0].version)
        assertEquals("report.pdf", result[0].filename)
        assertEquals("boom", result[0].error)
        assertEquals(1, result[0].attempts)
        assertEquals(4, result[0].maxRetries)
        assertEquals(true, result[0].canRetry) // 1 < 4
        // attempts 4 == cap 4 → no longer retryable; null payload tolerated.
        assertEquals(false, result[1].canRetry)
        assertEquals(null, result[1].filename)
        assertEquals(null, result[1].error)
    }

    @Test
    fun `scopes the query to the writable failed files with the given pageable`() {
        val failedFileIds = listOf(UUID.randomUUID(), UUID.randomUUID())
        val writable = setOf(failedFileIds.first())
        val pageable = slot<Pageable>()
        every { jobRepository.findDistinctFileidByStatus(jobStatus.FAILED) } returns failedFileIds
        every { fileAccessService.getWritableFileIds(failedFileIds, token) } returns writable
        every {
            jobRepository.findByStatusAndFileidInOrderByUpdatedAtDesc(
                jobStatus.FAILED, writable, capture(pageable),
            )
        } returns PageImpl(emptyList())

        service.listFailedJobs(token, PageRequest.of(3, 25))

        assertEquals(3, pageable.captured.pageNumber)
        assertEquals(25, pageable.captured.pageSize)
    }

    @Test
    fun `returns an empty page without checking permissions when there are no failed jobs`() {
        every { jobRepository.findDistinctFileidByStatus(jobStatus.FAILED) } returns emptyList()

        val result = service.listFailedJobs(token, PageRequest.of(0, 100))

        assertTrue(result.isEmpty)
        verify(exactly = 0) { fileAccessService.getWritableFileIds(any(), any()) }
        verify(exactly = 0) {
            jobRepository.findByStatusAndFileidInOrderByUpdatedAtDesc(any(), any(), any())
        }
    }

    @Test
    fun `returns an empty page without querying when none of the failed files are writable`() {
        val failedFileIds = listOf(UUID.randomUUID())
        every { jobRepository.findDistinctFileidByStatus(jobStatus.FAILED) } returns failedFileIds
        every { fileAccessService.getWritableFileIds(failedFileIds, token) } returns emptySet()

        val result = service.listFailedJobs(token, PageRequest.of(0, 100))

        assertTrue(result.isEmpty)
        verify(exactly = 0) {
            jobRepository.findByStatusAndFileidInOrderByUpdatedAtDesc(any(), any(), any())
        }
    }
}
