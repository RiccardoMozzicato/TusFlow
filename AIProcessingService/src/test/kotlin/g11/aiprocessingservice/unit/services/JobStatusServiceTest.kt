package g11.aiprocessingservice.unit.services

import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.JobStatusMessage
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.entities.JobOutbox
import g11.aiprocessingservice.repositories.JobOutboxRepository
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.DeadLetterService
import g11.aiprocessingservice.services.JobStatusService
import io.micrometer.tracing.Tracer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.messaging.simp.SimpMessagingTemplate
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals

class JobStatusServiceTest {
    private val jobRepository = mockk<JobRepository>()
    private val jobOutboxRepository = mockk<JobOutboxRepository>(relaxed = true)
    private val tracer = mockk<Tracer>(relaxed = true)
    private val deadLetterService = mockk<DeadLetterService>(relaxed = true)
    private val messagingTemplate = mockk<SimpMessagingTemplate>(relaxed = true)
    private val service =
        JobStatusService(jobRepository, jobOutboxRepository, tracer, deadLetterService, messagingTemplate)

    private val jobId = UUID.randomUUID()

    private fun job() = JobDTO(
        id = jobId,
        fileId = UUID.randomUUID(),
        version = 1,
        filename = "report.pdf",
        contentType = "application/pdf",
        ownerUserId = UUID.randomUUID(),
        fileVersionId = 1,
    )

    private fun entity(status: jobStatus = jobStatus.PENDING, attempts: UInt = 4u) = Job(
        jobid = jobId,
        fileid = UUID.randomUUID(),
        version = 1,
        type = "FILE_ANALYSIS",
        attempts = attempts,
        payload = mutableMapOf("filename" to "report.pdf"),
        status = status,
    )

    @Test
    fun `markJobAsDeadLettered sets terminal status, audits, and keeps the row`() {
        val saved = slot<Job>()
        every { jobRepository.findById(jobId) } returns Optional.of(entity())
        every { jobRepository.save(capture(saved)) } answers { firstArg() }
        every { jobOutboxRepository.save(any<JobOutbox>()) } answers { firstArg() }

        service.markJobAsDeadLettered(jobId, job(), "boom", "java.lang.IllegalStateException")

        // Soft-delete: terminal status, error recorded, row saved (never deleted).
        assertEquals(jobStatus.DEAD_LETTERED, saved.captured.status)
        assertEquals("boom", saved.captured.payload!!["error"])
        verify {
            deadLetterService.record("file-outbox", any<String>(), "java.lang.IllegalStateException", "boom")
        }
        verify(exactly = 0) { jobRepository.delete(any<Job>()) }
        verify(exactly = 0) { jobRepository.deleteById(any()) }
    }

    @Test
    fun `markJobAsDeadLettered emits a terminal ANALYSIS_DEAD_LETTERED outbox event`() {
        val outbox = slot<JobOutbox>()
        val jobDto = job()
        every { jobRepository.findById(jobId) } returns Optional.of(entity())
        every { jobRepository.save(any<Job>()) } answers { firstArg() }
        every { jobOutboxRepository.save(capture(outbox)) } answers { firstArg() }

        service.markJobAsDeadLettered(jobId, jobDto, "boom", "java.lang.IllegalStateException")

        // TusUploaderService still consumes this outbox event to persist the failure on the file.
        assertEquals("ANALYSIS_DEAD_LETTERED", outbox.captured.event_type)
        assertEquals(jobDto.fileId, outbox.captured.payload["fileid"])
        assertEquals(jobDto.version, outbox.captured.payload["version"])
        assertEquals(jobStatus.DEAD_LETTERED.name, outbox.captured.payload["status"])
        assertEquals("boom", outbox.captured.payload["error"])
    }

    @Test
    fun `notifyJobPending broadcasts PENDING to the owner without touching the DB`() {
        val jobDto = job()
        val message = slot<JobStatusMessage>()
        every {
            messagingTemplate.convertAndSendToUser(any(), any(), capture(message))
        } answers { }

        service.notifyJobPending(jobDto)

        // Broadcast-only: no status write, just the live ping for the in-progress indicator.
        verify(exactly = 0) { jobRepository.save(any<Job>()) }
        verify {
            messagingTemplate.convertAndSendToUser(jobDto.ownerUserId.toString(), "/queue/jobs", any<JobStatusMessage>())
        }
        assertEquals(jobStatus.PENDING.name, message.captured.status)
    }

    @Test
    fun `notifyJobProcessing broadcasts PROCESSING to the owner without touching the DB`() {
        val jobDto = job()
        val message = slot<JobStatusMessage>()
        every {
            messagingTemplate.convertAndSendToUser(any(), any(), capture(message))
        } answers { }

        service.notifyJobProcessing(jobDto)

        // PROCESSING is a live-only refinement of PENDING: nothing is persisted.
        verify(exactly = 0) { jobRepository.save(any<Job>()) }
        verify {
            messagingTemplate.convertAndSendToUser(jobDto.ownerUserId.toString(), "/queue/jobs", any<JobStatusMessage>())
        }
        assertEquals(jobStatus.PROCESSING.name, message.captured.status)
    }

    @Test
    fun `markJobAsDeadLettered broadcasts a terminal status to the owning user`() {
        val jobDto = job()
        every { jobRepository.findById(jobId) } returns Optional.of(entity())
        every { jobRepository.save(any<Job>()) } answers { firstArg() }
        every { jobOutboxRepository.save(any<JobOutbox>()) } answers { firstArg() }

        service.markJobAsDeadLettered(jobId, jobDto, "boom", "java.lang.IllegalStateException")

        // Per-user STOMP push so the Failed Jobs page learns the job was given up, live.
        verify {
            messagingTemplate.convertAndSendToUser(
                jobDto.ownerUserId.toString(),
                "/queue/jobs",
                any<JobStatusMessage>(),
            )
        }
    }
}
