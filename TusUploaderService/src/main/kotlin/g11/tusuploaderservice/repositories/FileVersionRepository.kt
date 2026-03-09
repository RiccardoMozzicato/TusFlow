package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.FileVersion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import java.util.Optional
import java.util.UUID

interface FileVersionRepository: JpaRepository<FileVersion,Long> {
    fun findByFileUuidAndVersion(uuid: UUID, version: Long): Optional<FileVersion>
}