package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

/**
 * A FAILED job surfaced to the dashboard's retry page. Carries everything the
 * UI needs to display the failure and trigger a manual retry via
 * `POST /api/v1/jobs?fileId=&version=`.
 *
 * @property attempts processing attempts already spent on this job
 * @property maxRetries the configured `ai.retry.max-user-retries` cap
 * @property canRetry whether a manual retry is still allowed (attempts <= cap)
 * @property error the last error message recorded on the job, if any
 * @property failedAt when the job last transitioned (its `updated_at`)
 */
data class FailedJobDTO(
    @JsonProperty("jobId")
    val jobId: UUID,

    @JsonProperty("fileId")
    val fileId: UUID,

    @JsonProperty("version")
    val version: Long,

    @JsonProperty("filename")
    val filename: String?,

    @JsonProperty("attempts")
    val attempts: Long,

    @JsonProperty("maxRetries")
    val maxRetries: Long,

    @JsonProperty("canRetry")
    val canRetry: Boolean,

    @JsonProperty("error")
    val error: String?,

    @JsonProperty("failedAt")
    val failedAt: Instant?,
)