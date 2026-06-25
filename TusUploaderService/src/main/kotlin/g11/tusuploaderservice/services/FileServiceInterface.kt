package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.DownloadDTO
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.FileShareDTO
import g11.tusuploaderservice.dto.FileVersionDTO
import g11.tusuploaderservice.dto.FileVersionFavoriteDTO
import g11.tusuploaderservice.dto.FiltersDTO
import g11.tusuploaderservice.dto.MetadataAIDTO
import g11.tusuploaderservice.dto.PendingUploadDTO
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.entities.Upload
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import java.util.UUID

interface FileServiceInterface {

    fun createFile(fileId: UUID, upload: Upload): File
    fun createFileVersion(upload: Upload,file: File): FileVersion

    fun getFiles(filtersDTO: FiltersDTO): Page<FileDTO>

    fun getFileMetadata(id: UUID): FileDTO
    fun getFileGeneratedMetadata(id: UUID, version: Long?): MetadataAIDTO
    fun updateFileMetadataByUser(id: UUID,version:Long?, metadata: Map<String, String>)
    fun updateGeneratedMetadataByUser(id: UUID, version: Long?, metadata: Map<String, String>)
    fun updateFileMetadata(id: UUID, version: Long?, metadata: Map<String, String>)
    fun deleteFile(id: UUID)
    fun downloadFile(id: UUID, version: Long? = null): DownloadDTO
    fun existsFileOrThrow(fileId: UUID)
    fun getFileShares(fileId: UUID, pageable: Pageable): Page<FileShareDTO>
    fun createFileShare(fileShareDTO: FileShareDTO): FileShareDTO
    /** The current user's resumable in-flight uploads (no offset; see [PendingUploadDTO]). */
    fun getMyPendingUploads(): List<PendingUploadDTO>

    fun getFilesSharedWithCurrentUser(
        ownerId: UUID? = null,
        permission: PERMISSION_LEVEL? = null,
        contentType: String? = null,
        dateFrom: String? = null,
        dateTo: String? = null,
        excludeContentTypes: Collection<String>? = null,
        pageable: Pageable,
    ): Page<FileDTO>
    fun deleteFileShare(fileId: UUID, shareId: Long)
    fun updateFileSharePermission(fileId: UUID, shareId: Long, permission: PERMISSION_LEVEL): FileShareDTO
    fun composeObject(upload: Upload)
    fun composeObjectClientSide(file: Upload)
    fun mergeObject(upload: Upload)
    fun moveSingleChunkUpload(upload: Upload)
    fun getCurrentUserPermission(fileId: UUID, userDTO: UserDTO): PERMISSION_LEVEL

    /**
     * Of [candidateIds], the file ids the current user (resolved from the request token) has
     * WRITE access to. An empty [candidateIds] returns all writable files (unbounded).
     */
    fun getWritableFileIds(candidateIds: Collection<UUID>): List<UUID>

    fun isOwner(fileId: UUID, userDTO: UserDTO): Boolean
    fun getUserDetails(token:String): UserDTO
    fun getUserFromUserService(token: String, userId: UUID): UserDTO

    // Version management
    fun getFileVersions(fileId: UUID): List<FileVersionDTO>
    fun downloadFileVersion(fileId: UUID, version: Long): DownloadDTO

    // Version favorites
    fun addFavorite(fileId: UUID, version: Long): FileVersionFavoriteDTO
    fun removeFavorite(fileId: UUID, version: Long)
    fun getMyFavorites(pageable: Pageable): Page<FileVersionFavoriteDTO>
    fun getFavoritedVersionsForFile(fileId: UUID): List<Long>
}