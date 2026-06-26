package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
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
@Schema(description = "Failed AI analysis job visible in the retry dashboard.")
data class FailedJobDTO(
    @JsonProperty("jobId")
    @Schema(description = "AI job id.", example = "7de8c224-a9e8-4f40-a882-46e48f08bfc5")
    val jobId: UUID,

    @JsonProperty("fileId")
    @Schema(description = "File id associated with the failed job.", example = "1bbba75b-35bb-4e88-9200-df55387c1570")
    val fileId: UUID,

    @JsonProperty("version")
    @Schema(description = "File version analyzed by the job.", example = "3")
    val version: Long,

    @JsonProperty("filename")
    @Schema(description = "Best-effort file name copied into the job payload.", example = "project-report.pdf")
    val filename: String?,

    @JsonProperty("attempts")
    @Schema(description = "Manual retry attempts already spent.", example = "1")
    val attempts: Long,

    @JsonProperty("maxRetries")
    @Schema(description = "Maximum number of manual retries allowed for this job.", example = "4")
    val maxRetries: Long,

    @JsonProperty("canRetry")
    @Schema(description = "Whether the authenticated user can still trigger a retry.", example = "true")
    val canRetry: Boolean,

    @JsonProperty("error")
    @Schema(description = "Last processing error recorded on the job.", example = "AI returned incomplete metadata")
    val error: String?,

    @JsonProperty("failedAt")
    @Schema(description = "When the job last transitioned to FAILED.", example = "2026-06-26T10:15:30Z")
    val failedAt: Instant?,
)
