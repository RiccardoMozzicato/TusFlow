package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.JobStatusMessage
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.JobOutbox
import g11.aiprocessingservice.repositories.JobOutboxRepository
import g11.aiprocessingservice.repositories.JobRepository
import io.micrometer.tracing.Tracer
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class JobStatusService(
    private val jobRepository: JobRepository,
    private val jobOutboxRepository: JobOutboxRepository,
    private val tracer: Tracer,
    private val deadLetterService: DeadLetterService,
    private val messagingTemplate: SimpMessagingTemplate,
) : JobStatusServiceInterface {
    private val log = LoggerFactory.getLogger(JobStatusService::class.java)
    private val objectMapper = jacksonObjectMapper()


    @Transactional
    override fun markJobAsCompleted(jobId: UUID, job: JobDTO, metadata: MetadataDTO) {
        val jobEntity = jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }

        var updatedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        updatedPayload["summary"] = metadata.summary!!
        updatedPayload["tags"] = metadata.tags!!
        updatedPayload["sensitivity"] = metadata.sensitivity!!.name
        updatedPayload["analysisStatus"] = jobStatus.COMPLETED.name

        // Prevent nested metadata: keep AI-enriched fields only
        updatedPayload.remove("metadata")

        jobEntity.payload = updatedPayload
        jobEntity.status = jobStatus.COMPLETED

        jobRepository.save(jobEntity)

        // Only the AI-analysis outputs are sent downstream as file metadata; job-internal fields
        // (ownerUserId, fileVersionId, contentType, filename) stay in the job payload and must not
        // pollute the file's persisted metadata. analysisStatus is omitted on success: the presence
        // of summary/tags already signals completion, and it is only carried for failed/unsupported.
        val analysisMetadata = mapOf(
            "summary" to metadata.summary!!,
            "tags" to metadata.tags!!,
            "sensitivity" to metadata.sensitivity!!.name,
        )

        // Create outbox event in the same transaction
        createOutboxEvent(
            event_type = "ANALYSIS_COMPLETED",
            payload = mapOf(
                "fileid" to job.fileId,
                "version" to job.version,
                "status" to jobStatus.COMPLETED.name,
                "metadata" to analysisMetadata
            )
        )

        broadcast(job, jobStatus.COMPLETED.name, metadata = updatedPayload)

        log.info("Job $jobId completed successfully with enhanced metadata")
    }


    @Transactional
    override fun markJobAsUnsupported(jobId: UUID, job: JobDTO, reason: String?) {
        val jobEntity = jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }

        var updatedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        updatedPayload["summary"] = "AI analysis not supported"
        updatedPayload["analysisStatus"] = "UNSUPPORTED"

        // Prevent nested metadata: keep AI-enriched fields only
        updatedPayload.remove("metadata")

        jobEntity.payload = updatedPayload
        jobEntity.status = jobStatus.UNSUPPORTED

        jobRepository.save(jobEntity)

        // Only the analysis status is sent downstream as file metadata; job-internal fields stay in
        // the job payload and must not pollute the file's persisted metadata.
        val analysisMetadata = mapOf(
            "analysisStatus" to "UNSUPPORTED",
        )

        // Create outbox event in the same transaction
        createOutboxEvent(
            event_type = "ANALYSIS_UNSUPPORTED",
            payload = mapOf(
                "fileid" to job.fileId,
                "version" to job.version,
                "status" to jobStatus.UNSUPPORTED.name,
                "metadata" to analysisMetadata,
                "reason" to (reason ?: "AI analysis failed after multiple attempts")
            )
        )

        broadcast(job, jobStatus.UNSUPPORTED.name, metadata = updatedPayload)

        log.info("Job $jobId marked as UNSUPPORTED with default metadata")
    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    override fun markJobAsFailed(jobId: UUID, job: JobDTO, errorMessage: String) {
        try {
            val jobEntity = jobRepository.findById(jobId)
            if (jobEntity.isPresent) {
                val entity = jobEntity.get()
                entity.status = jobStatus.FAILED

                // Update payload with error information
                val failedPayload = (entity.payload ?: mutableMapOf()).toMutableMap()
                failedPayload["error"] = errorMessage
                failedPayload["failedAt"] = System.currentTimeMillis()
                entity.payload = failedPayload

                jobRepository.save(entity)

                // Create outbox event in the same transaction
                createOutboxEvent(
                    event_type = "ANALYSIS_FAILED",
                    payload = mapOf(
                        "fileid" to job.fileId,
                        "version" to job.version,
                        "status" to jobStatus.FAILED.name,
                        "error" to errorMessage
                    )
                )

                broadcast(job, jobStatus.FAILED.name, error = errorMessage)

                log.info("Marked job $jobId as FAILED: $errorMessage")
            } else {
                log.warn("Could not find job $jobId to mark as FAILED")
            }
        } catch (ex: Exception) {
            log.error("Failed to mark job $jobId as FAILED: ${ex.message}", ex)
            throw ex // Rethrow to trigger transaction rollback
        }
    }


    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    override fun markJobAsDeadLettered(
        jobId: UUID,
        job: JobDTO,
        errorMessage: String,
        exceptionType: String?,
    ) {
        val jobEntity = jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }

        val failedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        failedPayload["error"] = errorMessage
        failedPayload["deadLetteredAt"] = System.currentTimeMillis()
        jobEntity.payload = failedPayload
        jobEntity.status = jobStatus.DEAD_LETTERED
        jobRepository.save(jobEntity)

        // Soft-delete: the row is kept (terminal status + history); the durable audit goes to
        // dead_letter_events.
        deadLetterService.record(
            originalTopic = "file-outbox",
            payload = objectMapper.writeValueAsString(failedPayload),
            exceptionType = exceptionType,
            exceptionMessage = errorMessage,
        )

        // Terminal notification for any live SSE subscriber (e.g. a just-triggered manual retry on
        // the Failed Jobs page). Without this the stream would hang until its server-side timeout,
        // then look like the job is "still processing" — when it has actually been given up.
        createOutboxEvent(
            event_type = "ANALYSIS_DEAD_LETTERED",
            payload = mapOf(
                "fileid" to job.fileId,
                "version" to job.version,
                "status" to jobStatus.DEAD_LETTERED.name,
                "error" to errorMessage,
            ),
        )

        broadcast(job, jobStatus.DEAD_LETTERED.name, error = errorMessage)

        log.error("Job $jobId dead-lettered after exhausting all retries: $errorMessage")
    }

    override fun notifyJobPending(job: JobDTO) {
        // The job is already persisted as PENDING by the caller (createJobIfNotExists / prepareRetry);
        // this only mirrors that onto the live channel so the in-progress indicator appears at once.
        broadcast(job, jobStatus.PENDING.name)
    }

    override fun notifyJobProcessing(job: JobDTO) {
        // Broadcast-only: PROCESSING is a live refinement of PENDING (the persisted status and the
        // aggregate "pending" counter stay unchanged), so there is nothing to save here.
        broadcast(job, jobStatus.PROCESSING.name)
    }

    /**
     * Pushes a job status to the owning user's STOMP queue. Best-effort: a messaging failure must
     * never roll back (or derail) the job transition, so it is logged and swallowed. Used for both
     * terminal outcomes (with metadata/error) and the non-terminal PENDING/PROCESSING pings.
     */
    private fun broadcast(
        job: JobDTO,
        status: String,
        metadata: Map<String, Any>? = null,
        error: String? = null,
    ) {
        try {
            messagingTemplate.convertAndSendToUser(
                job.ownerUserId.toString(),
                "/queue/jobs",
                JobStatusMessage(job.fileId, job.version, status, metadata, error),
            )
        } catch (e: Exception) {
            log.warn("Failed to broadcast job status {} for file {}: {}", status, job.fileId, e.message)
        }
    }


    private fun createOutboxEvent(
        event_type: String,
        payload: Map<String, Any>
    ) {
        val enrichedPayload = payload.toMutableMap().apply {
            tracer.currentSpan()?.context()?.let {
                put("traceparent", "00-${it.traceId()}-${it.spanId()}-01")
            }
        }
        val outboxEvent = JobOutbox(
            event_type = event_type,
            payload = enrichedPayload
        )
        jobOutboxRepository.save(outboxEvent)
        log.debug("Created outbox event: $event_type")
    }
}
