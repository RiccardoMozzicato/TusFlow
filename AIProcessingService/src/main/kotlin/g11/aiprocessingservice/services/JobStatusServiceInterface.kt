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

    /**
     * Terminal "given up" transition: the automatic Kafka retry and all the user's manual retries
     * have failed. Sets the job to DEAD_LETTERED (kept, but excluded from the retryable failed list)
     * and writes a dead_letter_events audit row.
     */
    fun markJobAsDeadLettered(jobId: UUID, job: JobDTO, errorMessage: String, exceptionType: String?)

    /**
     * Non-terminal live ping that a job has just (re)entered the PENDING queue, pushed over STOMP to
     * the owning user so the Library/Dashboard indicator turns "in progress" without a REST refetch.
     * Broadcast-only: the job is already persisted as PENDING, so there is no status write and no
     * outbox event. Best-effort, like the terminal broadcasts.
     */
    fun notifyJobPending(job: JobDTO)

    /**
     * Non-terminal live ping that AI analysis has actively started (PROCESSING), pushed over STOMP to
     * the owning user. Broadcast-only by design: the persisted status stays PENDING, since the
     * aggregate "pending" bucket counts PENDING + PROCESSING alike and the frontend folds 'processing'
     * back into the pending indicator — so PROCESSING is a live-only refinement with nothing to write.
     */
    fun notifyJobProcessing(job: JobDTO)
}

