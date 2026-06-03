package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.DownloadDTO
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.FileShareDTO
import g11.tusuploaderservice.dto.FileVersionDTO
import g11.tusuploaderservice.dto.FiltersDTO
import g11.tusuploaderservice.dto.MetadataAIDTO
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
    fun getAllFiles(): List<FileDTO>

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
    fun getFilesSharedWithCurrentUser( pageable: Pageable): Page<FileDTO>
    fun deleteFileShare(fileId: UUID, shareId: Long)
    fun composeObject(upload: Upload)
    fun composeObjectClientSide(file: Upload)
    fun mergeObject(upload: Upload)
    fun moveSingleChunkUpload(upload: Upload)
    fun getCurrentUserPermission(fileId: UUID, userDTO: UserDTO): PERMISSION_LEVEL
    fun isOwner(fileId: UUID, userDTO: UserDTO): Boolean
    fun getUserDetails(token:String): UserDTO
    fun getUserFromUserService(token: String, userId: UUID): UserDTO

    // Version management
    fun getFileVersions(fileId: UUID): List<FileVersionDTO>
    fun downloadFileVersion(fileId: UUID, version: Long): DownloadDTO
}