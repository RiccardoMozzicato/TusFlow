package g11.aiprocessingservice.services

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import g11.aiprocessingservice.repositories.JobRepository
import org.apache.kafka.common.errors.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class JobStatusService(
    private val jobRepository: JobRepository,
    private val kafkaTemplate: KafkaTemplate<String, String>,
) : JobStatusServiceInterface {
    private val objectMapper = jacksonObjectMapper()
    private val log = LoggerFactory.getLogger(JobStatusService::class.java)


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    override fun createJobIfNotExists(job: JobDTO): UUID? {
        val jobId = job.id
        log.debug("Checking if job already exists for file: {}, version: {}", job.fileId, job.version)

        val existingJob = jobRepository.findById(jobId)
        if (existingJob.isPresent) {
            val jobEntity = existingJob.get()
            return if (jobEntity.status == jobStatus.PENDING) {
                log.warn("Resuming stuck job: {}", jobEntity.jobid)
                jobId
            } else {
                log.info("Job {} already {}. Skipping.", jobEntity.jobid, jobEntity.status)
                null
            }
        }

        log.debug("Creating a new job")
        val payloadMap: MutableMap<String, Any> = mutableMapOf(
            "filename" to job.filename,
            "contentType" to job.contentType,
            "ownerUserId" to job.ownerUserId.toString(),
            "fileVersionId" to job.fileVersionId
        )
        job.metadata?.let { payloadMap["metadata"] = it as Any }

        jobRepository.save(Job(
            jobid = jobId,
            fileid = job.fileId,
            version = job.version,
            type = "FILE_ANALYSIS",
            payload = payloadMap,
            status = jobStatus.PENDING,
        ))
        return jobId
    }

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

        // Create outbox event in the same transaction
        publishJobEvent(
            eventType ="ANALYSIS_COMPLETED",
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

        var updatedPayload = (jobEntity.payload ?: mutableMapOf()).toMutableMap()
        updatedPayload["summary"] = "AI analysis not supported"
        updatedPayload["analysisStatus"] = "UNSUPPORTED"

        // Prevent nested metadata: keep AI-enriched fields only
        updatedPayload.remove("metadata")

        jobEntity.payload = updatedPayload
        jobEntity.status = jobStatus.UNSUPPORTED

        jobRepository.save(jobEntity)

        // Create outbox event in the same transaction
        publishJobEvent(
            eventType ="ANALYSIS_UNSUPPORTED",
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
                publishJobEvent(
                    eventType ="ANALYSIS_FAILED",
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


    private fun publishJobEvent(eventType: String, payload: Map<String, Any>) {
        val envelope = objectMapper.writeValueAsString(mapOf(
            "event_type" to eventType,
            "payload" to objectMapper.writeValueAsString(payload)
        ))
        kafkaTemplate.send("job-outbox", envelope)
        log.debug("Published job event: {}", eventType)
    }
}
