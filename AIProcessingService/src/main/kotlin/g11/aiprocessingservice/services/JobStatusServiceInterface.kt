package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.JobDTO
import g11.aiprocessingservice.dto.MetadataDTO
import java.util.*

interface JobStatusServiceInterface {

    /**
     * Marks a job as COMPLETED with enhanced metadata from AI analysis.
     * Creates an ANALYSIS_COMPLETED outbox event.
     */
    fun markJobAsCompleted(jobId: UUID, job: JobDTO, metadata: MetadataDTO)

    /**
     * Marks a job as UNSUPPORTED when AI analysis cannot process the file.
     * Creates an ANALYSIS_UNSUPPORTED outbox event.
     */
    fun markJobAsUnsupported(jobId: UUID, job: JobDTO, reason: String?)

    /**
     * Marks a job as FAILED when an error occurs during processing.
     * Creates an ANALYSIS_FAILED outbox event.
     */
    fun markJobAsFailed(jobId: UUID, job: JobDTO, errorMessage: String)
}

