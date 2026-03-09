package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.FileShare
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileShareRepository: JpaRepository<FileShare, Long> {
    fun findByFile_Uuid(fileId: UUID, pageable: Pageable): Page<FileShare>
    fun findByUserID(userID: UUID, pageable: Pageable): Page<FileShare>
    fun findByFile_UuidAndUserID(fileUuid: UUID, userID: UUID): FileShare?
    fun deleteByFile_Uuid(fileId: UUID)
}