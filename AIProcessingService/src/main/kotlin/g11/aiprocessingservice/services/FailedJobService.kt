package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.repositories.JobRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service

@Service
class FailedJobService(
    private val jobRepository: JobRepository,
    private val fileAccessService: FileAccessService,
    // Plain Int (not UInt): a Kotlin value class as a Spring-injected constructor parameter emits
    // a synthetic DefaultConstructorMarker arg that Spring then fails to resolve as a bean.
    @Value("\${ai.retry.max-user-retries:4}") private val maxUserRetries: Int,
) : FailedJobServiceInterface {

    override fun listFailedJobs(userToken: String, pageable: Pageable): Page<FailedJobDTO> {
        // Failed jobs are rare while a user's writable files can be many, so scope by the small
        // set: take the distinct files that actually have failures, ask which of those the user
        // may modify, then page within that subset. This keeps both the permission request and
        // the `IN` query bounded by the number of failing files, not the user's whole library.
        val failedFileIds = jobRepository.findDistinctFileidByStatus(jobStatus.FAILED)
        if (failedFileIds.isEmpty()) return Page.empty(pageable)

        val writableFileIds = fileAccessService.getWritableFileIds(failedFileIds, userToken)
        if (writableFileIds.isEmpty()) return Page.empty(pageable)

        return jobRepository
            .findByStatusAndFileidInOrderByUpdatedAtDesc(jobStatus.FAILED, writableFileIds, pageable)
            .map(::toFailedJobDTO)
    }

    private fun toFailedJobDTO(job: Job): FailedJobDTO {
        val payload = job.payload ?: emptyMap()
        return FailedJobDTO(
            jobId = job.jobid,
            fileId = job.fileid,
            version = job.version,
            filename = payload["filename"] as? String,
            attempts = job.attempts.toLong(),
            maxRetries = maxUserRetries.toLong(),
            // Retryable while fewer than the cap have been spent; at the cap the next failure
            // dead-letters the job, so no further manual retry is offered.
            canRetry = job.attempts.toLong() < maxUserRetries.toLong(),
            error = payload["error"] as? String,
            failedAt = job.updatedAt,
        )
    }
}