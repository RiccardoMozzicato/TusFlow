package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.FileVersionFavorite
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface FileVersionFavoriteRepository : JpaRepository<FileVersionFavorite, Long> {
    fun findByUserIdAndFileVersion_Id(userId: UUID, fileVersionId: Long): FileVersionFavorite?
    fun findByUserId(userId: UUID, pageable: Pageable): Page<FileVersionFavorite>
    fun findByUserIdAndFileVersion_File_Uuid(userId: UUID, fileUuid: UUID): List<FileVersionFavorite>
}
