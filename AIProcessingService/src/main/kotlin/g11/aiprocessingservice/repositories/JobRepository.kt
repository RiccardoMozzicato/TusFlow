package g11.aiprocessingservice.repositories

import g11.aiprocessingservice.entities.Job
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.UUID

interface JobRepository : JpaRepository<Job, UUID>, JpaSpecificationExecutor<Job> {

}