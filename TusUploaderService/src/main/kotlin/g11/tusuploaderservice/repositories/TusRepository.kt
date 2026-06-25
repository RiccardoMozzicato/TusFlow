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

    /**
     * Row-locked lookup by file-version id (SELECT ... FOR UPDATE), used to serialize the lazy
     * merge in [g11.tusuploaderservice.services.FileService.downloadFile]. On upload completion
     * the AI pipeline downloads each file twice (embedding + metadata) and the browser preview can
     * add a third — all triggering the merge at once. Locking here makes exactly one request
     * perform the merge; a successful merge deletes the upload row, so an empty result means a
     * concurrent request already merged this version.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findWithLockByFileVersionId(fileVersionId: Long): Optional<Upload>

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