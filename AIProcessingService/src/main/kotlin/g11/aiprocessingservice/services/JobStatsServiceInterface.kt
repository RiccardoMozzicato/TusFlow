package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.JobStatsDTO
import java.time.Instant

interface JobStatsServiceInterface {

    /**
     * Aggregate job counters for the dashboard, scoped to the files the caller has WRITE access
     * to (same authoritative policy as the failed-jobs list). Counts outside the user's writable
     * set are never surfaced.
     *
     * @param since optional ISO-8601 instant; when present, `completedSince` counts only jobs
     *        completed at or after it, otherwise all COMPLETED jobs in scope are counted
     * @param userToken the caller's JWT, used to resolve their writable files
     */
    fun getStats(since: Instant?, userToken: String): JobStatsDTO
}
