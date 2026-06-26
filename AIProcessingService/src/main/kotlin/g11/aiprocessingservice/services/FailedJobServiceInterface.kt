package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.JobDTO
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface FailedJobServiceInterface {

    /**
     * Returns a page of currently FAILED jobs the user has WRITE access to, newest first,
     * mapped to the dashboard's retry-page shape. Each entry carries the (fileId, version)
     * needed to drive a retry plus whether a manual retry is still permitted under the
     * user-retry cap.
     *
     * @param userToken the caller's JWT, used to resolve their writable files
     */
    fun listFailedJobs(userToken: String, pageable: Pageable): Page<FailedJobDTO>

    /**
     * Triggers a manual retry of the latest job for (fileId, version), enforcing the same
     * authoritative write-access policy as the failed-jobs list and the user-retry cap.
     * The actual reprocessing is fire-and-forget; the returned [RetryOutcome] tells the
     * caller how the request was resolved so it can map it to a transport-level response.
     *
     * @param userToken the caller's JWT, used to resolve their writable files
     */
    fun retryFailedJob(fileId: UUID, version: Long, userToken: String): RetryOutcome

    /**
     * Prepares a FAILED job for reprocessing: re-fetches its file metadata, resets the job to
     * PENDING and consumes one unit of the manual-retry budget (only after the file fetch succeeds,
     * so a failed prepare doesn't burn an attempt). Returns the [JobDTO] the AI pipeline reprocesses.
     *
     * @throws g11.aiprocessingservice.exceptions.JobNotFoundException if no job exists for [jobId]
     * @throws g11.aiprocessingservice.exceptions.JobNotFailedException if the job is not FAILED
     */
    fun prepareRetry(jobId: UUID): JobDTO
}

/**
 * Result of a manual retry request. Transport-agnostic: the controller maps each case to the
 * appropriate HTTP status, keeping servlet concerns out of the service.
 */
enum class RetryOutcome {
    /** Retry started (fire-and-forget). */
    ACCEPTED,

    /** Caller has no WRITE access to the file. */
    NO_WRITE_ACCESS,

    /** No job exists for the given (fileId, version). */
    JOB_NOT_FOUND,

    /** The user-retry cap has already been reached. */
    MAX_RETRIES_REACHED,

    /** The job is not in a FAILED state, so there is nothing to retry. */
    NOT_FAILED,
}