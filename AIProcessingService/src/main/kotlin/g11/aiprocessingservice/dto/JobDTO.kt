package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import java.util.UUID

@Schema(description = "Lifecycle status of an AI file-analysis job.")
enum class jobStatus {
    @Schema(description = "The job has been created and is waiting to be processed.")
    PENDING,
    @Schema(description = "The job is currently being processed.")
    PROCESSING,
    @Schema(description = "The file was analyzed successfully.")
    COMPLETED,
    @Schema(description = "The latest processing attempt failed but the job may still be retryable.")
    FAILED,
    @Schema(description = "The file content type is not supported for AI analysis.")
    UNSUPPORTED,

    /**
     * Terminal "given up" state: the automatic Kafka retry and all the user's manual retries failed.
     * The job is kept (history + a visible terminal status) but excluded from the retryable
     * failed-jobs list, and a row is written to dead_letter_events. Treated as terminal everywhere,
     * like COMPLETED/UNSUPPORTED, so a stray Kafka redelivery never reprocesses it.
     */
    DEAD_LETTERED
}

@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Internal event payload used to create or resume an AI file-analysis job.")
data class JobDTO(
    @JsonProperty("id")
    @Schema(description = "Outbox event id, reused as the AI job id.", example = "7de8c224-a9e8-4f40-a882-46e48f08bfc5")
    val id: UUID,  // ID dell'evento Kafka, usato come jobId

    @JsonProperty("fileId")
    @Schema(description = "Logical file id.", example = "1bbba75b-35bb-4e88-9200-df55387c1570")
    val fileId: UUID,

    @JsonProperty("version")
    @Schema(description = "File version number.", example = "3")
    val version: Long,

    @JsonProperty("filename")
    @Schema(description = "Original file name.", example = "project-report.pdf")
    val filename: String,

    @JsonProperty("metadata")
    @Schema(description = "Optional user-provided file metadata copied from the upload event.")
    val metadata: Map<String, String>? = null,

    @JsonProperty("contentType")
    @Schema(description = "File MIME type.", example = "application/pdf")
    val contentType: String,

    @JsonProperty("ownerUserId")
    @Schema(description = "Owner user id from Keycloak.", example = "3d7c30db-c0f1-45f4-b2cc-6cd89b785ef4")
    val ownerUserId: UUID,

    @JsonProperty("fileVersionId")
    @Schema(description = "Numeric database id of the file version.", example = "42")
    val fileVersionId: Long, // ID numerico della versione (non UUID)

    @JsonProperty("aiAttempts")
    @Schema(description = "AI attempts already recorded in the source event.", example = "0")
    val aiAttempts: UInt = 0u
)
