package g11.aiprocessingservice.repositories

import g11.aiprocessingservice.dto.jobStatus
import g11.aiprocessingservice.entities.Job
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface JobRepository : JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    fun findFirstByFileidAndVersionOrderByJobidDesc(fileId: UUID, version: Long): Job?

    /** All jobs for the given files (any version); callers reduce to the latest per (fileid, version). */
    fun findByFileidIn(fileIds: Collection<UUID>): List<Job>

    fun countByStatusIn(statuses: Collection<jobStatus>): Long

    fun countByStatus(status: jobStatus): Long

    fun countByStatusAndUpdatedAtGreaterThanEqual(status: jobStatus, since: Instant): Long

    /** Like [countByStatusIn] but restricted to jobs whose file is in [fileIds]. */
    fun countByStatusInAndFileidIn(statuses: Collection<jobStatus>, fileIds: Collection<UUID>): Long

    /** Like [countByStatus] but restricted to jobs whose file is in [fileIds]. */
    fun countByStatusAndFileidIn(status: jobStatus, fileIds: Collection<UUID>): Long

    /** Like [countByStatusAndUpdatedAtGreaterThanEqual] but restricted to jobs whose file is in [fileIds]. */
    fun countByStatusAndUpdatedAtGreaterThanEqualAndFileidIn(
        status: jobStatus,
        since: Instant,
        fileIds: Collection<UUID>,
    ): Long

    /** Distinct file ids that have at least one job in the given status. */
    @Query("select distinct j.fileid from Job j where j.status = :status")
    fun findDistinctFileidByStatus(status: jobStatus): List<UUID>

    /** Distinct file ids that have at least one job in any of the given statuses. */
    @Query("select distinct j.fileid from Job j where j.status in :statuses")
    fun findDistinctFileidByStatusIn(statuses: Collection<jobStatus>): List<UUID>

    /** A page of jobs in the given status whose file is in [fileIds], newest first. */
    fun findByStatusAndFileidInOrderByUpdatedAtDesc(
        status: jobStatus,
        fileIds: Collection<UUID>,
        pageable: Pageable,
    ): Page<Job>
}
