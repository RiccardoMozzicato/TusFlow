package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.exceptions.JobNotFailedException
import g11.aiprocessingservice.exceptions.JobNotFoundException
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.MetadataProcessor
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/jobs")
class JobControllers(
    private val metadataProcessor: MetadataProcessor,
    private val jobRepository: JobRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // Resolves the authenticated user (owner) id from the JWT for request logging.
    // Never throws so an API log line can't break the request flow.
    private fun currentUserOwnerId(): String =
        runCatching { (SecurityContextHolder.getContext().authentication.principal as Jwt).subject }
            .getOrDefault("unknown")

    /**
     * Get the status of a job processing AI metadata.
     * Returns job status and payload.
     *
     * @param fileId The UUID of the file
     * @param version The version of the file
     * @return JobStatusResponseDTO with current job status
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/status")
    fun getJobStatus(
        @RequestParam fileId: UUID,
        @RequestParam version: Long
    ): ResponseEntity<JobStatusResponseDTO> {
        log.debug("API GET /api/v1/jobs/status userOwnerId={} fileId={} version={}", currentUserOwnerId(), fileId, version)
        
        val job = jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, version)
        
        return if (job != null) {
            val response = JobStatusResponseDTO(
                jobId = job.jobid,
                fileId = job.fileid,
                version = job.version,
                status = job.status,
                payload = job.payload
            )
            log.debug("Job status for file {} version {}: {}", fileId, version, job.status)
            ResponseEntity.ok(response)
        } else {
            log.warn("No job found for fileId: {}, version: {}", fileId, version)
            ResponseEntity.status(HttpStatus.NOT_FOUND).build()
        }
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    fun retryFailedJob(
        @RequestParam fileId: UUID,
        @RequestParam version: Long,
        res: HttpServletResponse,
        @Value("\${ai.retry.max-user-retries:4}") maxUserRetries: UInt
    ) {
        log.debug("API POST /api/v1/jobs userOwnerId={} fileId={} version={}", currentUserOwnerId(), fileId, version)

        val job = jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, version)

        if (job == null) {
            log.warn("Retry requested for non-existing job, file {} v{}", fileId, version)
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "job not found")
            return
        } else if(job.attempts > maxUserRetries) {
            log.info("Retry denied for job {}, maximum retries reached", job.jobid)
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "max attempts number reached")
            return
        }

        val jobId: UUID = job.jobid

        // Validazione veloce: se non è FAILED, rispondi subito con 409, senza avviare nulla
        if (job.status != jobStatus.FAILED) {
            log.info("Retry denied for job {}: the status '{}' is not failed", job.jobid, job.status)
            res.sendError(HttpServletResponse.SC_CONFLICT, "job is not failed")
            return
        }

        log.info("Retrying failed job {}", jobId)

        // fire-and-forget
        metadataProcessor.retryFailedJobAsync(jobId)

        res.status = HttpServletResponse.SC_ACCEPTED
    }
}
