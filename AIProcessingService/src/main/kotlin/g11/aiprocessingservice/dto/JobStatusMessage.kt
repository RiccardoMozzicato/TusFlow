package g11.aiprocessingservice.dto

import java.util.UUID

/**
 * Live job-status update pushed over STOMP to the owning user's `/queue/jobs` destination.
 *
 * Self-contained so the frontend can update the Library/Dashboard/Failed Jobs views straight from
 * the message, without a follow-up REST round-trip:
 *  - [metadata] is populated for COMPLETED/UNSUPPORTED (summary, tags, sensitivity, ...).
 *  - [error] is populated for FAILED/DEAD_LETTERED.
 */
data class JobStatusMessage(
    val fileId: UUID,
    val version: Long,
    val status: String,
    val metadata: Map<String, Any>? = null,
    val error: String? = null,
)
