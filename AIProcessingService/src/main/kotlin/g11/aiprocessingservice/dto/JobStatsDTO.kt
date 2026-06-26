package g11.aiprocessingservice.dto

import io.swagger.v3.oas.annotations.media.Schema

/**
 * Aggregate job counters for the dashboard.
 *
 * @property pending jobs waiting for or currently in processing (PENDING + PROCESSING)
 * @property completedSince jobs COMPLETED at or after the requested `since` instant
 *                          (all COMPLETED jobs when no `since` was given)
 * @property failed jobs currently in FAILED state
 */
@Schema(description = "Aggregate AI job counters scoped to files writable by the authenticated user.")
data class JobStatsDTO(
    @Schema(description = "Jobs currently pending or processing.", example = "2")
    val pending: Long,
    @Schema(description = "Completed jobs since the requested instant, or all completed jobs when no instant is provided.", example = "14")
    val completedSince: Long,
    @Schema(description = "Jobs currently in FAILED state and visible for retry.", example = "1")
    val failed: Long,
)
