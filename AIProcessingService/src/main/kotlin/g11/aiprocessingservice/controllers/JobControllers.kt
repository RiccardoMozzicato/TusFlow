package g11.aiprocessingservice.controllers

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.FailedJobsPageDTO
import g11.aiprocessingservice.dto.JobKeyDTO
import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.dto.JobStatusResponseDTO
import g11.aiprocessingservice.services.FailedJobServiceInterface
import g11.aiprocessingservice.services.JobStatsServiceInterface
import g11.aiprocessingservice.services.JobStatusServiceInterface
import g11.aiprocessingservice.services.RetryOutcome
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
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
@Tag(
    name = "AI Job Status",
    description = "Read AI analysis job state, dashboard counters and retryable failures.",
)
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
    @Operation(
        summary = "Get AI job statuses for many file versions",
        description = "Returns the latest AI job for each requested `(fileId, version)` pair. Pairs without an AI job are omitted. At most 200 keys are accepted.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            description = "File/version keys to inspect.",
            content = [
                Content(
                    array = ArraySchema(schema = Schema(implementation = JobKeyDTO::class)),
                    examples = [
                        ExampleObject(
                            name = "Two document versions",
                            value = """[
  {"fileId":"1bbba75b-35bb-4e88-9200-df55387c1570","version":1},
  {"fileId":"1bbba75b-35bb-4e88-9200-df55387c1570","version":2}
]"""
                        )
                    ]
                )
            ]
        ),
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Latest job statuses found for the requested keys.",
                content = [
                    Content(array = ArraySchema(schema = Schema(implementation = JobStatusResponseDTO::class)))
                ]
            ),
            ApiResponse(responseCode = "400", description = "More than 200 keys were submitted.", content = [Content()]),
            ApiResponse(responseCode = "401", description = "Missing or invalid JWT.", content = [Content()])
        ]
    )
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
    @Operation(
        summary = "Get AI job dashboard counters",
        description = "Returns pending, completed and failed counters scoped to files writable by the authenticated user. The optional `since` parameter narrows only the completed counter.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "AI job counters.",
                content = [Content(schema = Schema(implementation = JobStatsDTO::class))]
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid JWT.", content = [Content()])
        ]
    )
    fun getJobStats(
        @Parameter(
            description = "Optional ISO-8601 instant. When present, `completedSince` counts only jobs completed at or after this instant.",
            example = "2026-06-26T00:00:00Z"
        )
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
    @Operation(
        summary = "List failed AI jobs",
        description = "Returns retryable FAILED jobs visible to the authenticated user, newest first. The list is scoped using the same write-access policy as manual retry.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "A page of failed AI jobs.",
                content = [Content(schema = Schema(implementation = FailedJobsPageDTO::class))]
            ),
            ApiResponse(responseCode = "401", description = "Missing or invalid JWT.", content = [Content()])
        ]
    )
    fun listFailedJobs(
        @Parameter(description = "Zero-based page index.", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, clamped to 1..200.", example = "100")
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
    @Tag(name = "AI Job Retry")
    @Operation(
        summary = "Retry a failed AI job",
        description = "Starts a fire-and-forget retry for the latest AI job associated with the given file/version. The request is accepted only when the caller has write access, the job exists, the job is FAILED and the manual retry cap has not been reached.",
        responses = [
            ApiResponse(responseCode = "202", description = "Retry accepted and started asynchronously.", content = [Content()]),
            ApiResponse(responseCode = "401", description = "Missing or invalid JWT.", content = [Content()]),
            ApiResponse(responseCode = "403", description = "The caller cannot write the file, or the retry cap has been reached.", content = [Content()]),
            ApiResponse(responseCode = "404", description = "No AI job exists for the file/version.", content = [Content()]),
            ApiResponse(responseCode = "409", description = "The latest job is not in FAILED state.", content = [Content()])
        ]
    )
    fun retryFailedJob(
        @Parameter(description = "File id whose latest failed job should be retried.", example = "1bbba75b-35bb-4e88-9200-df55387c1570")
        @RequestParam fileId: UUID,
        @Parameter(description = "File version whose latest failed job should be retried.", example = "3")
        @RequestParam version: Long,
        @Parameter(hidden = true)
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
