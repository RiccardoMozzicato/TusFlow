package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.File
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.Optional
import java.util.UUID

interface FileRepository : JpaRepository<File, UUID>, JpaSpecificationExecutor<File> {
    fun findByUuid(uuid: UUID): Optional<File>
}