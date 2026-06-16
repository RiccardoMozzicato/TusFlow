package g11.aiprocessingservice.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

enum class jobStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
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
data class JobDTO(
    @JsonProperty("id")
    val id: UUID,  // ID dell'evento Kafka, usato come jobId

    @JsonProperty("fileId")
    val fileId: UUID,

    @JsonProperty("version")
    val version: Long,

    @JsonProperty("filename")
    val filename: String,

    @JsonProperty("metadata")
    val metadata: Map<String, String>? = null,

    @JsonProperty("contentType")
    val contentType: String,

    @JsonProperty("ownerUserId")
    val ownerUserId: UUID,

    @JsonProperty("fileVersionId")
    val fileVersionId: Long, // ID numerico della versione (non UUID)

    @JsonProperty("aiAttempts")
    val aiAttempts: UInt = 0u
)
