package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TusRepository: JpaRepository<Upload, UUID>, JpaSpecificationExecutor<Upload> {

    fun existsByFileId(fileUuid: UUID): Boolean

    fun findAllByExpiresAtBeforeAndStatusNot(now: Date, uploadStatus: UploadStatus): List<Upload>

    fun findByStatus(
        status: UploadStatus,
    ): MutableList<Upload>

    fun findByFileVersionId(fileVersionId: Long): Optional<Upload>
}