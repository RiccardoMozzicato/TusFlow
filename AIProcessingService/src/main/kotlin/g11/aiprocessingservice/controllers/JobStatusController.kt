package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.repositories.JobRepository
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/jobs")
class JobStatusController(
    private val jobRepository: JobRepository
) {
    private val log = LoggerFactory.getLogger(this::class.java)

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
        log.debug("Fetching job status for fileId: {}, version: {}", fileId, version)
        
        val jobs = jobRepository.findLatestByFileIdAndVersion(fileId, version)
        val job = jobs.firstOrNull()
        
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
}
