package g11.aiprocessingservice.dto

/**
 * Aggregate job counters for the dashboard.
 *
 * @property pending jobs waiting for or currently in processing (PENDING + PROCESSING)
 * @property completedSince jobs COMPLETED at or after the requested `since` instant
 *                          (all COMPLETED jobs when no `since` was given)
 * @property failed jobs currently in FAILED state
 */
data class JobStatsDTO(
    val pending: Long,
    val completedSince: Long,
    val failed: Long,
)
