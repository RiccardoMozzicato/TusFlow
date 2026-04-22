package g11.aiprocessingservice.repositories

import g11.aiprocessingservice.entities.Job
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface JobRepository : JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {
    @Query("SELECT j FROM Job j WHERE j.fileid = :fileId AND j.version = :version ORDER BY j.jobid DESC", 
           nativeQuery = false)
    fun findLatestByFileIdAndVersion(
        @Param("fileId") fileId: UUID,
        @Param("version") version: Long
    ): List<Job>
}