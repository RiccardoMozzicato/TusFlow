package g11.tusuploaderservice.repositories

import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface TusRepository: JpaRepository<Upload, UUID>, JpaSpecificationExecutor<Upload> {

    /**
     * Acquires a row-level write lock (SELECT ... FOR UPDATE) on the upload so that a
     * concurrent chunk append (PATCH) and a termination (DELETE) of the same upload are
     * serialized. This prevents the lost update where an in-flight PATCH commits
     * status=UPLOADING after a DELETE has already set status=DELETED.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockById(id: UUID): Optional<Upload>

    fun existsByFileId(fileUuid: UUID): Boolean

    fun findAllByExpiresAtBeforeAndStatusNot(now: Date, uploadStatus: UploadStatus): List<Upload>

    /**
     * The caller's resumable uploads: owned by [ownerId], still in one of the given
     * [statuses] (PENDING/UPLOADING — the client still has bytes to send), and not yet
     * expired ([expiresAt] strictly after [now]). Drives GET /api/v1/files/my-uploads.
     */
    fun findByOwnerIdAndStatusInAndExpiresAtAfter(
        ownerId: UUID,
        statuses: Collection<UploadStatus>,
        now: Date,
    ): List<Upload>

    fun findByStatus(
        status: UploadStatus,
    ): MutableList<Upload>

    fun findByFileVersionId(fileVersionId: Long): Optional<Upload>
}