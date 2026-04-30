package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.JobOutbox
import g11.aiprocessingservice.repositories.JobOutboxRepository
import g11.aiprocessingservice.repositories.JobRepository
import io.micrometer.tracing.Tracer
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class JobStatusService(
    private val jobRepository: JobRepository,
    private val jobOutboxRepository: JobOutboxRepository,
    private val tracer: Tracer,
) : JobStatusServiceInterface {
    private val log = LoggerFactory.getLogger(JobStatusService::class.java)


    @Transactional
    override fun markJobAsCompleted(jobId: UUID, job: JobDTO, metadata: MetadataDTO) {
        val jobEntity = jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }

        val updatedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        updatedPayload["summary"] = metadata.summary!!
        updatedPayload["tags"] = metadata.tags!!
        updatedPayload["sensitivity"] = metadata.sensitivity!!.name
        updatedPayload["analysisStatus"] = jobStatus.COMPLETED.name

        jobEntity.payload = updatedPayload
        jobEntity.status = jobStatus.COMPLETED

        jobRepository.save(jobEntity)

        // Create outbox event in the same transaction
        createOutboxEvent(
            event_type = "ANALYSIS_COMPLETED",
            payload = mapOf(
                "fileid" to job.fileId,
                "version" to job.version,
                "status" to jobStatus.COMPLETED.name,
                "metadata" to updatedPayload
            )
        )

        log.info("Job $jobId completed successfully with enhanced metadata")
    }


    @Transactional
    override fun markJobAsUnsupported(jobId: UUID, job: JobDTO, reason: String?) {
        val jobEntity = jobRepository.findById(jobId)
            .orElseThrow { ResourceNotFoundException("Job not found: $jobId") }

        val updatedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        updatedPayload["summary"] = "AI analysis not supported"
        updatedPayload["analysisStatus"] = "UNSUPPORTED"

        jobEntity.payload = updatedPayload
        jobEntity.status = jobStatus.UNSUPPORTED

        jobRepository.save(jobEntity)

        // Create outbox event in the same transaction
        createOutboxEvent(
            event_type = "ANALYSIS_UNSUPPORTED",
            payload = mapOf(
                "fileid" to job.fileId,
                "version" to job.version,
                "status" to jobStatus.UNSUPPORTED.name,
                "metadata" to updatedPayload,
                "reason" to (reason ?: "AI analysis failed after multiple attempts")
            )
        )

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

                log.info("Marked job $jobId as FAILED: $errorMessage")
            } else {
                log.warn("Could not find job $jobId to mark as FAILED")
            }
        } catch (ex: Exception) {
            log.error("Failed to mark job $jobId as FAILED: ${ex.message}", ex)
            throw ex // Rethrow to trigger transaction rollback
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
