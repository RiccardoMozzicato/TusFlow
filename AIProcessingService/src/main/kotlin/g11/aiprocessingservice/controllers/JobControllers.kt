package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.JobKeyDTO
import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.exceptions.JobNotFailedException
import g11.aiprocessingservice.exceptions.JobNotFoundException
import g11.aiprocessingservice.repositories.JobRepository
import g11.aiprocessingservice.services.FailedJobServiceInterface
import g11.aiprocessingservice.services.FileAccessService
import g11.aiprocessingservice.services.JobStatsServiceInterface
import g11.aiprocessingservice.services.MetadataProcessor
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping("/api/v1/jobs")
class JobControllers(
    private val metadataProcessor: MetadataProcessor,
    private val jobRepository: JobRepository,
    private val failedJobService: FailedJobServiceInterface,
    private val jobStatsService: JobStatsServiceInterface,
    private val fileAccessService: FileAccessService,
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    /**
     * The caller's raw JWT, used to resolve their permissions in TusUploaderService.
     * Endpoints are `@PreAuthorize("isAuthenticated()")`, so a non-JWT principal is unexpected.
     */
    private fun currentUserToken(): String {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return (principal as? Jwt)?.tokenValue
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "missing authentication token")
    }

    // Resolves the authenticated user (owner) id from the JWT for request logging.
    // Never throws so an API log line can't break the request flow.
    private fun currentUserOwnerId(): String =
        runCatching { (SecurityContextHolder.getContext().authentication.principal as Jwt).subject }
            .getOrDefault("unknown")

    /**
     * Returns the current AI-job status for many files in one call, so the document
     * library can flag each card. Pairs without a job are simply omitted from the
     * response (= "no AI job"). One query; the latest job per (fileId, version) is
     * selected (findFirst...OrderByJobidDesc semantics).
     */
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/status/batch")
    fun getJobStatusBatch(
        @RequestBody keys: List<JobKeyDTO>
    ): ResponseEntity<List<JobStatusResponseDTO>> {
        if (keys.isEmpty()) return ResponseEntity.ok(emptyList())
        if (keys.size > MAX_BATCH_KEYS) {
            return ResponseEntity.badRequest().build()
        }

        // Latest job per (fileid, version), mirroring findFirst...OrderByJobidDesc.
        val latestByKey = jobRepository.findByFileidIn(keys.map { it.fileId }.distinct())
            .groupBy { it.fileid to it.version }
            .mapValues { (_, jobs) -> jobs.maxBy { it.jobid } }

        val responses = keys.distinct().mapNotNull { key ->
            latestByKey[key.fileId to key.version]?.let { job ->
                JobStatusResponseDTO(
                    jobId = job.jobid,
                    fileId = job.fileid,
                    version = job.version,
                    status = job.status,
                    payload = job.payload
                )
            }
        }
        return ResponseEntity.ok(responses)
    }

    /**
     * Aggregate job counters for the dashboard, scoped to the files the caller can write
     * (same authoritative write-access policy as the failed-jobs list and retry).
     *
     * @param since optional ISO-8601 instant; when present, `completedSince`
     *        counts only jobs completed at or after it (e.g. start of today
     *        in the caller's timezone). When absent, all COMPLETED jobs in scope count.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/stats")
    fun getJobStats(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        since: Instant?
    ): JobStatsDTO = jobStatsService.getStats(since, currentUserToken())

    /**
     * Lists currently FAILED jobs for the dashboard's retry page, newest first,
     * as a paginated response. Each entry carries the (fileId, version) needed to
     * drive [retryFailedJob] plus whether a manual retry is still permitted under
     * the user-retry cap.
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/failed")
    fun listFailedJobs(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
    ): Page<FailedJobDTO> {
        val pageRequest = PageRequest.of(
            page.coerceAtLeast(0),
            size.coerceIn(1, MAX_FAILED_PAGE_SIZE),
        )
        return failedJobService.listFailedJobs(currentUserToken(), pageRequest)
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

        // Only allow retrying jobs whose file the caller can modify (same authoritative
        // write-access policy as the failed-jobs list); otherwise the listing scope would be
        // cosmetic and any user could retry any job by fileId/version. Check just this one file.
        if (fileId !in fileAccessService.getWritableFileIds(listOf(fileId), currentUserToken())) {
            res.sendError(HttpServletResponse.SC_FORBIDDEN, "no write access to this file")
            return
        }

        val job = jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, version)

        if (job == null) {
            log.warn("Retry requested for non-existing job, file {} v{}", fileId, version)
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "job not found")
            return
        } else if (job.attempts >= maxUserRetries) {
            log.info("Retry denied for job {}, maximum retries reached", job.jobid)
            // attempts counts user retries already spent; once it reaches the cap, no more are
            // allowed (the next failure would dead-letter the job anyway).
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

    private companion object {
        /** Upper bound on a batch status request, to keep the IN query bounded. */
        const val MAX_BATCH_KEYS = 200

        /** Upper bound on the failed-jobs page size, to keep the query bounded. */
        const val MAX_FAILED_PAGE_SIZE = 200
    }
}
