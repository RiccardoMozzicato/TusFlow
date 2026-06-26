package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.JobKeyDTO
import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.services.FailedJobServiceInterface
import g11.aiprocessingservice.services.JobStatsServiceInterface
import g11.aiprocessingservice.services.JobStatusServiceInterface
import g11.aiprocessingservice.services.RetryOutcome
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
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
    private val failedJobService: FailedJobServiceInterface,
    private val jobStatsService: JobStatsServiceInterface,
    private val jobStatusService: JobStatusServiceInterface,
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
        if (keys.size > MAX_BATCH_KEYS) {
            return ResponseEntity.badRequest().build()
        }
        return ResponseEntity.ok(jobStatusService.getJobStatusBatch(keys))
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
    ) {
        log.debug("API POST /api/v1/jobs userOwnerId={} fileId={} version={}", currentUserOwnerId(), fileId, version)

        // The write-access policy, retry cap and FAILED-state checks all live in the service; the
        // controller only maps the resulting outcome onto a transport-level response.
        when (failedJobService.retryFailedJob(fileId, version, currentUserToken())) {
            RetryOutcome.NO_WRITE_ACCESS ->
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "no write access to this file")
            RetryOutcome.JOB_NOT_FOUND ->
                res.sendError(HttpServletResponse.SC_NOT_FOUND, "job not found")
            RetryOutcome.MAX_RETRIES_REACHED ->
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "max attempts number reached")
            RetryOutcome.NOT_FAILED ->
                res.sendError(HttpServletResponse.SC_CONFLICT, "job is not failed")
            RetryOutcome.ACCEPTED ->
                res.status = HttpServletResponse.SC_ACCEPTED
        }
    }

    private companion object {
        /** Upper bound on a batch status request, to keep the IN query bounded. */
        const val MAX_BATCH_KEYS = 200

        /** Upper bound on the failed-jobs page size, to keep the query bounded. */
        const val MAX_FAILED_PAGE_SIZE = 200
    }
}
