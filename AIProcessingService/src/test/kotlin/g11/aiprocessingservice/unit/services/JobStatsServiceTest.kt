package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.FileAccessService
import g11.aiprocessingservice.services.JobStatsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals

class JobStatsServiceTest {
    private lateinit var jobRepository: JobRepository
    private lateinit var fileAccessService: FileAccessService
    private lateinit var service: JobStatsService

    private val token = "user-jwt"
    private val reportedStatuses =
        listOf(jobStatus.PENDING, jobStatus.PROCESSING, jobStatus.COMPLETED, jobStatus.FAILED)
    private val pendingStatuses = listOf(jobStatus.PENDING, jobStatus.PROCESSING)

    @BeforeEach
    fun setup() {
        jobRepository = mockk()
        fileAccessService = mockk()
        service = JobStatsService(jobRepository, fileAccessService)
    }

    @Test
    fun `counts only jobs whose file is writable, all-completed when no since given`() {
        val candidates = listOf(UUID.randomUUID(), UUID.randomUUID())
        val writable = setOf(candidates.first())
        every { jobRepository.findDistinctFileidByStatusIn(reportedStatuses) } returns candidates
        every { fileAccessService.getWritableFileIds(candidates, token) } returns writable
        every { jobRepository.countByStatusInAndFileidIn(pendingStatuses, writable) } returns 3
        every { jobRepository.countByStatusAndFileidIn(jobStatus.COMPLETED, writable) } returns 128
        every { jobRepository.countByStatusAndFileidIn(jobStatus.FAILED, writable) } returns 2

        val stats = service.getStats(since = null, userToken = token)

        assertEquals(3, stats.pending)
        assertEquals(128, stats.completedSince)
        assertEquals(2, stats.failed)
        verify(exactly = 0) {
            jobRepository.countByStatusAndUpdatedAtGreaterThanEqualAndFileidIn(any(), any(), any())
        }
    }

    @Test
    fun `scopes completed count to the since instant when provided`() {
        val candidates = listOf(UUID.randomUUID())
        val writable = candidates.toSet()
        val since = Instant.parse("2026-06-04T00:00:00Z")
        every { jobRepository.findDistinctFileidByStatusIn(reportedStatuses) } returns candidates
        every { fileAccessService.getWritableFileIds(candidates, token) } returns writable
        every { jobRepository.countByStatusInAndFileidIn(pendingStatuses, writable) } returns 0
        every {
            jobRepository.countByStatusAndUpdatedAtGreaterThanEqualAndFileidIn(
                jobStatus.COMPLETED, since, writable,
            )
        } returns 5
        every { jobRepository.countByStatusAndFileidIn(jobStatus.FAILED, writable) } returns 0

        val stats = service.getStats(since = since, userToken = token)

        assertEquals(5, stats.completedSince)
        verify(exactly = 0) { jobRepository.countByStatusAndFileidIn(jobStatus.COMPLETED, any()) }
    }

    @Test
    fun `returns zeroed stats without checking permissions when there are no jobs`() {
        every { jobRepository.findDistinctFileidByStatusIn(reportedStatuses) } returns emptyList()

        val stats = service.getStats(since = null, userToken = token)

        assertEquals(0, stats.pending)
        assertEquals(0, stats.completedSince)
        assertEquals(0, stats.failed)
        verify(exactly = 0) { fileAccessService.getWritableFileIds(any(), any()) }
        verify(exactly = 0) { jobRepository.countByStatusInAndFileidIn(any(), any()) }
    }

    @Test
    fun `returns zeroed stats without counting when none of the files are writable`() {
        val candidates = listOf(UUID.randomUUID())
        every { jobRepository.findDistinctFileidByStatusIn(reportedStatuses) } returns candidates
        every { fileAccessService.getWritableFileIds(candidates, token) } returns emptySet()

        val stats = service.getStats(since = null, userToken = token)

        assertEquals(0, stats.pending)
        assertEquals(0, stats.completedSince)
        assertEquals(0, stats.failed)
        verify(exactly = 0) { jobRepository.countByStatusInAndFileidIn(any(), any()) }
        verify(exactly = 0) { jobRepository.countByStatusAndFileidIn(any(), any()) }
    }
}
