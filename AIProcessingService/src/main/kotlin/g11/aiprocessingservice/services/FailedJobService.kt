package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.FailedJobDTO
import g11.aiprocessingservice.dto.FileDTO
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.entities.toDTO
import g11.aiprocessingservice.exceptions.JobNotFailedException
import g11.aiprocessingservice.exceptions.JobNotFoundException
import g11.aiprocessingservice.repositories.JobRepository
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class FailedJobService(
    private val jobRepository: JobRepository,
    private val fileAccessService: FileAccessServiceInterface,
    // @Lazy breaks the FailedJobService <-> MetadataProcessor constructor cycle: retryFailedJob kicks
    // off async reprocessing here, while MetadataProcessor.retryFailedJobAsync calls back into
    // prepareRetry below. Same proxy pattern used by TusUploaderService's TusService self-injection.
    @Lazy private val metadataProcessor: MetadataProcessorInterface,
    private val authService: AuthServiceInterface,
    @Qualifier("TusUploaderService") private val fileServiceClient: RestClient,
    // Plain Int (not UInt): a Kotlin value class as a Spring-injected constructor parameter emits
    // a synthetic DefaultConstructorMarker arg that Spring then fails to resolve as a bean.
    @Value("\${ai.retry.max-user-retries:4}") private val maxUserRetries: Int,
) : FailedJobServiceInterface {
    private val log = LoggerFactory.getLogger(FailedJobService::class.java)

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

    override fun retryFailedJob(fileId: UUID, version: Long, userToken: String): RetryOutcome {
        // Only allow retrying jobs whose file the caller can modify (same authoritative write-access
        // policy as the failed-jobs list); otherwise the listing scope would be cosmetic and any user
        // could retry any job by fileId/version. Check just this one file.
        if (fileId !in fileAccessService.getWritableFileIds(listOf(fileId), userToken)) {
            return RetryOutcome.NO_WRITE_ACCESS
        }

        val job = jobRepository.findFirstByFileidAndVersionOrderByJobidDesc(fileId, version)
        if (job == null) {
            log.warn("Retry requested for non-existing job, file {} v{}", fileId, version)
            return RetryOutcome.JOB_NOT_FOUND
        }

        // attempts counts user retries already spent; once it reaches the cap, no more are allowed
        // (the next failure would dead-letter the job anyway).
        if (job.attempts >= maxUserRetries.toUInt()) {
            log.info("Retry denied for job {}, maximum retries reached", job.jobid)
            return RetryOutcome.MAX_RETRIES_REACHED
        }

        // Fast validation: if it is not FAILED there is nothing to retry, so reject without starting.
        if (job.status != jobStatus.FAILED) {
            log.info("Retry denied for job {}: the status '{}' is not failed", job.jobid, job.status)
            return RetryOutcome.NOT_FAILED
        }

        log.info("Retrying failed job {}", job.jobid)
        // fire-and-forget
        metadataProcessor.retryFailedJobAsync(job.jobid)
        return RetryOutcome.ACCEPTED
    }

    @Transactional
    override fun prepareRetry(jobId: UUID): JobDTO {
        val job = jobRepository.findById(jobId).orElseThrow { JobNotFoundException() }

        if (job.status != jobStatus.FAILED) {
            throw JobNotFailedException()
        }

        try {
            val token = authService.getAccessToken()

            log.info("Fetching file metadata for job {}", jobId)

            val file = fileServiceClient.get()
                .uri { uriBuilder ->
                    uriBuilder
                        .path("/api/v1/files/${job.fileid}")
                        .build()
                }
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .body(FileDTO::class.java)

            log.info("Fetched file: $file")

            if (file == null) {
                throw ResourceNotFoundException("Problem with file: ${job.fileid}")
            }

            job.status = jobStatus.PENDING
            // A user retry consumes one unit of the manual-retry budget. The automatic Kafka path
            // does NOT increment, so `attempts` reflects user retries only (capped by
            // max-user-retries). Incremented here — after the file fetch succeeds — so a failed
            // prepare doesn't burn an attempt.
            job.attempts += 1u
            jobRepository.save(job)

            return job.toDTO(file)
        } catch (ex: RestClientResponseException) {
            log.error("Error calling file service for file {}: {}", job.fileid, ex.message)
            if (ex.statusCode.value() == 404) {
                throw ResourceNotFoundException("Problem with file: ${job.fileid}")
            }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "File service error: ${ex.statusText}")
        }
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