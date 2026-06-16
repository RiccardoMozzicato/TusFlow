package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.FileDTO
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.toDTO
import g11.aiprocessingservice.exceptions.JobNotFailedException
import g11.aiprocessingservice.exceptions.JobNotFoundException
import g11.aiprocessingservice.repositories.JobRepository
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class RetryJobService(
    private val jobRepository: JobRepository,
    private val authService: AuthServiceInterface,
    @Qualifier("TusUploaderService") private val fileServiceClient: RestClient,
) {
    private val log = LoggerFactory.getLogger(RetryJobService::class.java)

    @Transactional
    fun prepareRetry(jobId: UUID): JobDTO {
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
}