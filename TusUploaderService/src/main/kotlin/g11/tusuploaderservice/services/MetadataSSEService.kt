package g11.tusuploaderservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.util.UUID

@Service
class MetadataSSEService(
    @Qualifier("aiProcessingServiceM2MClient") private val aiProcessingServiceClient: RestClient,
    private val fileService: FileServiceInterface
) {
    private val log = LoggerFactory.getLogger(MetadataSSEService::class.java)
    private val objectMapper = jacksonObjectMapper()

    private enum class AiJobStatusEnum {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
        UNSUPPORTED
    }

    private data class AiJobStatusDTO(
        val jobId: UUID? = null,
        val fileId: UUID? = null,
        val version: Long? = null,
        val status: AiJobStatusEnum,
        val payload: Map<String, Any>? = null
    )

    private companion object {
        const val MAX_RETRIES = 30  // 30 attempts
        const val INITIAL_DELAY_MS = 1000L  // 1 second
        const val MAX_DELAY_MS = 5000L  // 5 seconds max
    }

    /**
     * Stream metadata processing status via Server-Sent Events (SSE).
     * Polls the AIProcessingService until the job is COMPLETED, FAILED, or UNSUPPORTED.
     * Returns the final metadata when ready.
     *
     * @param fileId The UUID of the file
     * @param version The version of the file (optional, defaults to current version)
     * @return StreamingResponseBody that emits SSE events
     */
    fun streamMetadataProcessingStatus(fileId: UUID, version: Long?): StreamingResponseBody {
        return StreamingResponseBody { output ->
            try {
                var attempt = 0
                var lastStatus = AiJobStatusEnum.PENDING

                while (attempt < MAX_RETRIES) {
                    if (Thread.currentThread().isInterrupted) {
                        log.debug("SSE stream interrupted for file {}", fileId)
                        break
                    }

                    attempt++
                    log.debug("Polling job status for file {} - attempt {}/{}", fileId, attempt, MAX_RETRIES)

                    try {
                        // Query AIProcessingService for job status
                        val jobStatus = getJobStatus(fileId, version)
                        lastStatus = jobStatus.status

                        when (jobStatus.status) {
                            AiJobStatusEnum.COMPLETED -> {
                                log.debug("Job COMPLETED for file {} version {}", fileId, version)
                                // Fetch full metadata from FileService
                                val metadata = fileService.getFileGeneratedMetadata(fileId, version)
                                sendSSEEvent(output, "completed", metadata)
                                return@StreamingResponseBody
                            }

                            AiJobStatusEnum.FAILED -> {
                                log.warn("Job FAILED for file {} version {}", fileId, version)
                                sendSSEEvent(output, "failed", mapOf("error" to "AI analysis failed"))
                                return@StreamingResponseBody
                            }

                            AiJobStatusEnum.UNSUPPORTED -> {
                                log.warn("Job UNSUPPORTED for file {} version {}", fileId, version)
                                sendSSEEvent(output, "unsupported", mapOf("error" to "File type not supported for AI analysis"))
                                return@StreamingResponseBody
                            }

                            AiJobStatusEnum.PENDING, AiJobStatusEnum.PROCESSING -> {
                                log.debug("Job still processing: {} - attempt {}", jobStatus.status, attempt)
                                sendSSEEvent(output, "processing", mapOf("status" to jobStatus.status.toString(), "attempt" to attempt))
                            }
                        }

                        // Calculate exponential backoff delay
                        if (attempt < MAX_RETRIES) {
                            val delayMs = calculateBackoff(attempt)
                            log.debug("Waiting {}ms before next poll", delayMs)
                            Thread.sleep(delayMs)
                        }

                    } catch (e: RestClientResponseException) {
                        if (e.statusCode.value() == 404) {
                            // Job not yet created, wait and retry
                            log.debug("Job not found yet for file {} - attempt {}", fileId, attempt)
                            sendSSEEvent(output, "waiting", mapOf("message" to "Waiting for job to be created"))
                            if (attempt < MAX_RETRIES) {
                                Thread.sleep(calculateBackoff(attempt))
                            }
                        } else {
                            log.error("Error querying job status: {}", e.message, e)
                            sendSSEEvent(output, "error", mapOf("error" to "Failed to query job status: ${e.message}"))
                            return@StreamingResponseBody
                        }
                    } catch (e: Exception) {
                        log.error("Unexpected error in SSE stream: {}", e.message, e)
                        sendSSEEvent(output, "error", mapOf("error" to "Unexpected error: ${e.message}"))
                        return@StreamingResponseBody
                    }
                }

                // Max retries reached
                log.warn("Max retries reached for file {} - last status was: {}", fileId, lastStatus)
                val errorMessage = if (lastStatus == AiJobStatusEnum.PENDING || lastStatus == AiJobStatusEnum.PROCESSING) {
                    "AI analysis is taking longer than expected. The file may be too large or complex. Please try again later."
                } else {
                    "AI analysis could not be completed."
                }
                sendSSEEvent(output, "timeout", mapOf("error" to errorMessage))

            } catch (e: Exception) {
                log.error("Fatal error in SSE stream for file {}: {}", fileId, e.message, e)
                try {
                    sendSSEEvent(output, "error", mapOf("error" to "Internal server error"))
                } catch (sseError: Exception) {
                    log.error("Failed to send error SSE event: {}", sseError.message)
                }
            }
        }
    }

    /**
     * Query the AIProcessingService for the current job status.
     */
    private fun getJobStatus(fileId: UUID, version: Long?): AiJobStatusDTO {
        val targetVersion = version ?: 1L
        log.debug("Querying job status from AIProcessingService for file {} version {}", fileId, targetVersion)

        return aiProcessingServiceClient
            .get()
            .uri("/api/v1/jobs/status?fileId={fileId}&version={version}", fileId, targetVersion)
            .retrieve()
            .body(AiJobStatusDTO::class.java) ?: throw IllegalStateException("Failed to retrieve job status")
    }

    /**
     * Send an SSE event to the client.
     */
    private fun sendSSEEvent(output: java.io.OutputStream, eventType: String, data: Any) {
        try {
            val json = objectMapper.writeValueAsString(data)
            val sseMessage = "event: $eventType\ndata: $json\n\n"
            output.write(sseMessage.toByteArray())
            output.flush()
            log.debug("Sent SSE event: {}", eventType)
        } catch (e: Exception) {
            log.error("Error sending SSE event: {}", e.message, e)
            throw e
        }
    }

    /**
     * Calculate exponential backoff with jitter, capped at MAX_DELAY_MS.
     */
    private fun calculateBackoff(attempt: Int): Long {
        val exponentialDelay = INITIAL_DELAY_MS * attempt
        return Math.min(exponentialDelay, MAX_DELAY_MS)
    }
}
