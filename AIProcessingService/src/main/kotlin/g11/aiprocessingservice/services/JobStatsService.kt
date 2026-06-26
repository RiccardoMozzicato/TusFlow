package g11.aiprocessingservice.services

import g11.aiprocessingservice.dto.JobStatsDTO
import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.repositories.JobRepository
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class JobStatsService(
    private val jobRepository: JobRepository,
    private val fileAccessService: FileAccessServiceInterface,
) : JobStatsServiceInterface {

    override fun getStats(since: Instant?, userToken: String): JobStatsDTO {
        // Same inversion as the failed-jobs list (see [FailedJobService]): take the distinct files
        // that actually have a reportable job, ask which of those the user may modify, then count
        // within that subset. This bounds both the permission request and the count queries by the
        // number of files with jobs rather than the user's whole library.
        val candidateFileIds = jobRepository.findDistinctFileidByStatusIn(REPORTED_STATUSES)
        if (candidateFileIds.isEmpty()) return EMPTY_STATS

        val writableFileIds = fileAccessService.getWritableFileIds(candidateFileIds, userToken)
        if (writableFileIds.isEmpty()) return EMPTY_STATS

        val completedSince =
            if (since != null) {
                jobRepository.countByStatusAndUpdatedAtGreaterThanEqualAndFileidIn(
                    jobStatus.COMPLETED, since, writableFileIds,
                )
            } else {
                jobRepository.countByStatusAndFileidIn(jobStatus.COMPLETED, writableFileIds)
            }

        return JobStatsDTO(
            pending = jobRepository.countByStatusInAndFileidIn(PENDING_STATUSES, writableFileIds),
            completedSince = completedSince,
            failed = jobRepository.countByStatusAndFileidIn(jobStatus.FAILED, writableFileIds),
        )
    }

    private companion object {
        /** Statuses that contribute to a counter; used to build the candidate file set. */
        val REPORTED_STATUSES = listOf(
            jobStatus.PENDING, jobStatus.PROCESSING, jobStatus.COMPLETED, jobStatus.FAILED,
        )

        /** Statuses counted as "pending" on the dashboard. */
        val PENDING_STATUSES = listOf(jobStatus.PENDING, jobStatus.PROCESSING)

        val EMPTY_STATS = JobStatsDTO(pending = 0, completedSince = 0, failed = 0)
    }
}
