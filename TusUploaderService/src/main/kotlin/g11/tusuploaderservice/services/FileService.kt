package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.*
import g11.tusuploaderservice.dto.toDTO
import g11.tusuploaderservice.entities.*
import g11.tusuploaderservice.exceptions.ForbiddenException
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.exceptions.StorageException
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.FileShareRepository
import g11.tusuploaderservice.repositories.FileVersionRepository
import g11.tusuploaderservice.repositories.TusRepository
import g11.tusuploaderservice.specs.FileSpecifications
import g11.tusuploaderservice.utils.getUserIdFromJwt
import g11.tusuploaderservice.utils.getUserTokenFromJwt
import g11.tusuploaderservice.utils.mergeByteArrays
import io.micrometer.tracing.Tracer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Transactional
@Service
class FileService(
    private val tusRepository: TusRepository,
    private val fileRepository: FileRepository,
    private val storageService: StorageServiceInterface,
    private val authService: AuthService,
    private val fileShareRepository: FileShareRepository,
    @Qualifier("userServiceClient") private val userServiceClient: RestClient,
    private val fileVersionRepository: FileVersionRepository,
    private val outboxRepository: g11.tusuploaderservice.repositories.FilesOutboxEventsRepository,
    private val tracer: Tracer,
) : FileServiceInterface {
    private val log = LoggerFactory.getLogger(FileService::class.java)

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun createFile(fileId: UUID, upload: Upload) : File {

        //ACLs controls not needed here because this function is called on upload completion by appendChunk, thus is, checks are already done

        val filename = upload.filename ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

        val fileEntity = File(
            uuid = fileId,
            createdAt = Date.from(Instant.now()),
            lastUpdateAt =  Date.from(Instant.now()),
            lastFilename = filename,
            currentVersion = 1L,
            ownerId = upload.ownerId,
            organization = upload.organization,
            department = upload.department,
            sharingScope = upload.sharingScope,
        )

        log.debug("Created file ${fileEntity.uuid}")
        fileRepository.save(fileEntity)
        return fileEntity
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun createFileVersion(upload: Upload,file: File): FileVersion {
        //ACLs controls not needed here because this function is called on upload completion by appendChunk, thus is, checks are already done

        val filename = upload.filename ?: LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))

        val fileVersion = FileVersion(
            filename = filename,
            size = upload.size!!, // size is not null here because upload is completed
            metadata = upload.metadata,
            aiMetadata = null,
            contentType = upload.contentType!!, // contentType is not null here because its autodetected before completion
            createdAt = upload.createdAt,
            objectKey = upload.objectKey!!, // objectKey is not null here because upload is completed
            checksum = upload.checksum,
            status = FileStatus.MERGE_PENDING,
            lastUpdateAt = Date.from(Instant.now()),
            file = null,
        )
        // collega al File e calcola il numero di versione
        file.addNewVersion(fileVersion)

        // salva solo File; grazie al cascade su versions verrà salvata anche FileVersion
        val savedFile = fileRepository.save(file)

        // recupera la versione appena aggiunta dalla collection del file salvato
        val persistedVersion = savedFile.versions
            .first { it.version == fileVersion.version && it.filename == fileVersion.filename }

        // collega l'Upload tramite l'id della versione appena persistita
        //l'upload serve per fare il merge delle parti, dopo di che verrà cancellato
        upload.fileVersionId = persistedVersion.id
        tusRepository.save(upload)

        log.info("Created new file version ${persistedVersion.version} for file ${savedFile.uuid}")
        return persistedVersion
    }

    /**
     * Restituisce file con sharingScope ORGANIZATION o DEPARTMENT
     * Filtrati per organization e department dell'utente corrente
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getOrganizationDepartmentFiles(filtersDTO: FiltersDTO): Page<FileDTO> {
        log.debug("Retrieving organization/department files with criteria {}", filtersDTO)

        // Verifica che lo sharingScope sia ORGANIZATION o DEPARTMENT
        require(filtersDTO.sharingScope == SharingScope.ORGANIZATION ||
                filtersDTO.sharingScope == SharingScope.DEPARTMENT) {
            "getOrganizationDepartmentFiles requires sharingScope to be ORGANIZATION or DEPARTMENT"
        }

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        val pageable = PageRequest.of(
            filtersDTO.pageNumber.toInt(),
            filtersDTO.pageSize.toInt(),
            Sort.by(filtersDTO.sortDirection, filtersDTO.sortField)
        )

        val spec = FileSpecifications.buildOrganizationDepartmentFiles(
            contentType = filtersDTO.contentType,
            dateFrom = filtersDTO.dateFrom,
            dateTo = filtersDTO.dateTo,
            sharingScope = filtersDTO.sharingScope,
            organization = userDetails.organization,
            department = userDetails.department
        )

        val files = fileRepository.findAll(spec, pageable)

        if(files.isEmpty) {
            log.warn("No organization/department file found")
            throw ResourceNotFoundException("No files found matching the specified criteria")
        }

        // Batch request per ottenere i nomi degli owner
        val ownerIds = files.content.map { it.ownerId }.distinct()
        val ownerNamesMap = getUsersBatchFromUserService(token, ownerIds)

        return files.map {
            val ownerName = ownerNamesMap[it.ownerId]?.fullName
            it.toDTO(ownerName)
        }
    }

    /**
     * Restituisce file con sharingScope PERSONAL
     * Include file dove l'utente è owner O è presente in fileShare
     */
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getPersonalFiles(filtersDTO: FiltersDTO): Page<FileDTO> {
        log.debug("Retrieving personal files with criteria {}", filtersDTO)

        // Verifica che lo sharingScope sia PERSONAL (o usa PERSONAL come default)
        val sharingScope = filtersDTO.sharingScope
        require(sharingScope == SharingScope.PERSONAL) {
            "getPersonalFiles requires sharingScope to be PERSONAL"
        }

        // Get service token to call UserDetailService
        val token = authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        val pageable = PageRequest.of(
            filtersDTO.pageNumber.toInt(),
            filtersDTO.pageSize.toInt(),
            Sort.by(filtersDTO.sortDirection, filtersDTO.sortField)
        )

        log.debug("pageable: {}", pageable)
        val spec = FileSpecifications.buildPersonalFiles(
            contentType = filtersDTO.contentType,
            dateFrom = filtersDTO.dateFrom,
            dateTo = filtersDTO.dateTo,
            ownerId = userDetails.id,
            userId = userDetails.id
        )

        log.debug("Specification: {}", spec.toString())
        val files = fileRepository.findAll(spec, pageable)

        log.debug("files: {}", files.toString())

        if(files.isEmpty) {
            log.warn("No personal file found")
            throw ResourceNotFoundException("No files found matching the specified criteria")
        }

        // Batch request per ottenere i nomi degli owner
        val ownerIds = files.content.map { it.ownerId }.distinct()
        val ownerNamesMap = getUsersBatchFromUserService(token, ownerIds)

        return files.map {
            val ownerName = ownerNamesMap[it.ownerId]?.fullName
            it.toDTO(ownerName)
        }
    }

    /**
     * Restituisce tutti i file presenti nel repository
     */
    override fun getAllFiles(): List<FileDTO> {
        log.debug("Retrieving all files from repository")

        val files = fileRepository.findAll()

        if(files.isEmpty()) {
            log.warn("No files found in repository")
            throw ResourceNotFoundException("No files found in the repository")
        }

        log.info("Found {} files in repository", files.size)
        return files.map { it.toDTO() }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getFileMetadata(id: UUID): FileDTO {
        log.debug("Retrieving metadata for {}", id)

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions - if no exception is thrown, user has at least READ_ONLY access
        getCurrentUserPermission(id, userDetails)

        return fileRepository.findById(id)
            .orElseThrow{
                log.error("File: {}, does not exists", id)  // Should not happen due to permission check above
                ResourceNotFoundException("Resource does not exists, no metadata found for: $id")
            }
            .toDTO()
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getFileGeneratedMetadata(id: UUID, version: Long?): MetadataAIDTO {
        log.debug("Retrieving AI-generated metadata for file: {} version: {}", id, version)

        val token = authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions - if no exception is thrown, user has at least READ_ONLY access
        getCurrentUserPermission(id, userDetails)

        // Get the file to determine version
        val file = fileRepository.findById(id)
            .orElseThrow {
                log.error("File: {}, does not exist", id)
                ResourceNotFoundException("File with id: $id not found")
            }

        val targetVersion = version ?: file.currentVersion

        // Find the specific file version
        val fileVersion = fileVersionRepository.findByFileUuidAndVersion(id, targetVersion)
            .orElseThrow {
                log.error("File version: {}/{} does not exist", id, targetVersion)
                ResourceNotFoundException("File version $targetVersion for file $id not found")
            }

        // Extract AI metadata fields from the dedicated aiMetadata map
        val aiMetadata = fileVersion.aiMetadata ?: emptyMap()

        return MetadataAIDTO(
            tags = aiMetadata["tags"],
            summary = aiMetadata["summary"],
            filename = fileVersion.filename,
            metadata = null,
            contentType = fileVersion.contentType,
            ownerUserId = file.ownerId.toString(),
            sensitivity = aiMetadata["sensitivity"],
            fileVersionId = fileVersion.id?.toString(),
            status = fileVersion.status
        )
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff', 'ROLE_ai_worker')")
    override fun updateFileMetadataByUser(id: UUID, version:Long?, metadata: Map<String, String>) {
        log.debug("Updating metadata of {} with {}", id, metadata)

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions
        if(getCurrentUserPermission(id, userDetails) != PERMISSION_LEVEL.READ_WRITE) {
            log.warn("User {} does not have WRITE permission for file {}", userDetails.id, id)
            throw ForbiddenException("Access denied: insufficient permissions to update metadata for file $id")
        }

        updateFileMetadata(id, version, metadata)
    }

    /**
     * Internal method to update file metadata without authentication checks.
     * Used by Kafka consumers and other internal services.
     */
    override fun updateFileMetadata(id: UUID, version: Long?, metadata: Map<String, String>) {
        val span = tracer.nextSpan().name("updateFileMetadata").start()
        val scope = tracer.withSpan(span)
        try {
            log.debug("Updating metadata internally of {} with {}", id, metadata)

            val file = fileVersionRepository.findByFileUuidAndVersion(id, version ?: 1L)
                .orElseThrow {
                    log.warn("The resource $id does not exists")
                    ResourceNotFoundException("Resource does not exist, cannot update metadata for: $id")
                }

            // Extract fileName from metadata if provided
            metadata["filename"]?.let { file.filename = it }

            // Filter out fileName from metadata map for the metadata field update
            var metadataToUpdate = metadata - "filename"

            // Split AI-related metadata keys from the general metadata map
            val aiMetadataKeys = setOf("tags", "summary", "sensitivity")
            val aiMetadataToUpdate = metadataToUpdate.filterKeys { it in aiMetadataKeys }
            metadataToUpdate = metadataToUpdate - aiMetadataKeys

            log.debug("updating file metadata: {}, for file {}", metadataToUpdate.toString(), id)

            // Update metadata fields that exist in the entity and eventually
            // add new fields specified in the metadata map
            val currentMetadata = file.metadata?.toMutableMap() ?: mutableMapOf()
            currentMetadata.putAll(metadataToUpdate)
            file.metadata = currentMetadata

            if (aiMetadataToUpdate.isNotEmpty()) {
                val currentAiMetadata = file.aiMetadata?.toMutableMap() ?: mutableMapOf()
                currentAiMetadata.putAll(aiMetadataToUpdate)
                file.aiMetadata = currentAiMetadata
            }
        } finally {
            scope.close()
            span.end()
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun deleteFile(id: UUID) {
        //WARNING: Actually a user can deletes a file even if is not the owner but has WRITE permission
        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions
        if(getCurrentUserPermission(id, userDetails) != PERMISSION_LEVEL.READ_WRITE) {
            log.warn("User {} does not have WRITE permission for file {}", userDetails.id, id)
            throw ForbiddenException("Access denied: insufficient permissions to delete file $id")
        }

        val fileOptional = fileRepository.findById(id)
        if (!fileOptional.isPresent) {
            log.warn("The resource $id does not exists")
            throw ResourceNotFoundException("Resource does not exists: $id")
        }

        val file = fileOptional.get()

        // Recupera gli ID delle versioni prima di cancellare il file
        val fileVersionIds = file.versions.map { it.id!! }
        log.debug("File {} has {} versions to delete: {}", id, fileVersionIds.size, fileVersionIds)

        // Delete all file shares first to avoid foreign key constraint violation
        log.debug("Deleting all shares for file {}", id)
        fileShareRepository.deleteByFile_Uuid(id)
        
        // Pubblica evento di cancellazione nell'outbox prima di cancellare il file
        try {
            val deletionEvent = mapOf(
                "fileId" to id.toString(),
                "deletedAt" to Instant.now().toString(),
                "deletedBy" to userDetails.id.toString(),
                "fileVersionIds" to fileVersionIds.map { it.toString() },
                "traceparent" to tracer.currentSpan()?.context()
                    ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
            )

            log.info("Creating FILE_DELETED event with payload: $deletionEvent")

            val outboxEvent = FilesOutboxEvent(
                eventType = "FILE_DELETED",
                payload = deletionEvent,
                createdAt = Date.from(Instant.now())
            )

            outboxRepository.save(outboxEvent)
            log.info("Successfully saved FILE_DELETED outbox event for file {} with {} versions", id, fileVersionIds.size)
        } catch (e: Exception) {
            log.error("Failed to publish FILE_DELETED event for file {}: {}", id, e.message, e)
            // Continua comunque con la cancellazione del file
        }

        fileRepository.deleteById(id)
        storageService.deleteAllVersions(id.toString())

        log.info("File {} successfully deleted with {} versions", id, fileVersionIds.size)
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff','ROLE_ai_worker')")
    override fun downloadFile(id: UUID,  version: Long?): DownloadDTO {
        log.debug("Download the resource {}", id)

        // AI service accounts (ROLE_ai_worker) are not registered in UserDetailService,
        // so skip the user lookup and permission check. The ROLE_ai_worker JWT authority
        // already guarantees the caller is a trusted internal service.
        val isAiWorker = SecurityContextHolder.getContext().authentication.authorities
            .any { it.authority == "ROLE_ai_worker" }

        if (!isAiWorker) {
            val token = authService.getAccessToken()
            val userDetails = getUserDetails(token)
            log.debug("Current user details: {}", userDetails)
            // Check permissions - if no exception is thrown, user has at least READ_ONLY access
            getCurrentUserPermission(id, userDetails)
        }

        // Retrieve the file entity
        val file = fileRepository.findById(id).orElseThrow {
            log.warn("The resource $id does not exists")
            ResourceNotFoundException("Resource does not exists: $id")
        }

        // Retrieve information about the current version
        //if version is null, download the current version
        val fileVersion: FileVersion = if(version == null) {
            file.versions.find { it.version == file.currentVersion }
                ?: throw ResourceNotFoundException("Current version not found for file $id")
        } else{
            file.versions.find { it.version == version }
                ?: throw ResourceNotFoundException("Version $version not found for file $id")
        }

        val currentStatus = fileVersion.status
        val contentType = fileVersion.contentType

        // If the file is already merged, download directly
        if (currentStatus != FileStatus.MERGE_PENDING) {
            log.debug("Already merged, so downloading directly!")
            val key = fileVersion.objectKey
                ?: "${file.uuid}/v${fileVersion.version}/${fileVersion.filename}"
            val stream = storageService.getObject("files", key)
            return DownloadDTO(fileVersion.filename, contentType, stream)
        }

        // Otherwise, merge first
        log.debug("File not merged, merging now!")

        // Recupera l'upload a partire da fileVersionId
        val versionId = fileVersion.id
            ?: throw ResourceNotFoundException("FileVersion id not found for file $id")

        val upload = tusRepository.findByFileVersionId(versionId)
            .orElseThrow {
                log.warn("Upload for file $id and version ${fileVersion.version} not found")
                ResourceNotFoundException("Upload for file $id not found")
            }

        if (upload.chunkCount == 1) {
            moveSingleChunkUpload(upload)
        } else {
            mergeObject(upload)
        }

        // Dopo il merge, imposta objectKey finale e stato AVAILABLE
        val finalObjectKey = "${file.uuid}/v${fileVersion.version}/${fileVersion.filename}"
        fileVersion.objectKey = finalObjectKey
        fileVersion.status = FileStatus.AVAILABLE
        fileVersionRepository.save(fileVersion)

        tusRepository.delete(upload)

        val stream: ByteArray = storageService.getObject(bucket = "files", fileVersion.objectKey)
        return DownloadDTO(file.lastFilename, contentType, stream)
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun existsFileOrThrow(fileId: UUID) {
        if (!fileRepository.existsById(fileId)) {
            log.warn("The resource $fileId does not exists")
            throw ResourceNotFoundException("Resource does not exists: $fileId")
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getCurrentUserPermission(fileId: UUID, userDTO: UserDTO): PERMISSION_LEVEL {
        val file = fileRepository.findById(fileId)
            .orElseThrow { ResourceNotFoundException("File not found: $fileId") }
        // Owner always has READ_WRITE
        if (file.ownerId == userDTO.id) {
            return PERMISSION_LEVEL.READ_WRITE
        }

        // Check explicit file share
        val fileShare = fileShareRepository.findByFile_UuidAndUserID(fileId, userDTO.id)
        if (fileShare != null) {
            return fileShare.permission
        }

        // Check based on sharing scope and role
        when (file.sharingScope) {
            SharingScope.PERSONAL -> {
                // Personal files: no access unless owner or explicitly shared (already checked above)
                throw ForbiddenException("Access denied: file is personal and not shared with user ${userDTO.id}")
            }

            SharingScope.DEPARTMENT -> {
                // Department files: check organization match first
                if (file.organization != userDTO.organization) {
                    throw ForbiddenException("Access denied: file belongs to different organization")
                }

                // Admin: READ_WRITE on any file in their organization (can access any department)
                if (userDTO.role == UserRole.ROLE_admin) {
                    return PERMISSION_LEVEL.READ_WRITE
                }

                // For non-admin roles, check department match
                if (file.department != userDTO.department) {
                    throw ForbiddenException("Access denied: file belongs to different department")
                }

                // Manager: READ_WRITE on department files
                if (userDTO.role == UserRole.ROLE_manager) {
                    return PERMISSION_LEVEL.READ_WRITE
                }

                // Staff: READ_ONLY on department files
                if (userDTO.role == UserRole.ROLE_staff) {
                    return PERMISSION_LEVEL.READ_ONLY
                }

                throw ForbiddenException("Access denied: insufficient permissions")
            }

            SharingScope.ORGANIZATION -> {
                // Organization files: check organization match
                if (file.organization != userDTO.organization) {
                    throw ForbiddenException("Access denied: file belongs to different organization")
                }

                // Admin: READ_WRITE on any file in their organization
                if (userDTO.role == UserRole.ROLE_admin) {
                    return PERMISSION_LEVEL.READ_WRITE
                }

                // Manager: READ_ONLY on organization files
                if (userDTO.role == UserRole.ROLE_manager) {
                    return PERMISSION_LEVEL.READ_ONLY
                }

                // Staff: READ_ONLY on organization files
                if (userDTO.role == UserRole.ROLE_staff) {
                    return PERMISSION_LEVEL.READ_ONLY
                }

                throw ForbiddenException("Access denied: insufficient permissions")
            }
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getFileShares(fileId: UUID, pageable: Pageable): Page<FileShareDTO> {

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions - if no exception is thrown, user has at least READ_ONLY access
        getCurrentUserPermission(fileId, userDetails)

        // Ensure the file exists before fetching shares
        if (!fileRepository.existsById(fileId)) {
            throw ResourceNotFoundException("File not found: $fileId")
        }
        val shares = fileShareRepository.findByFile_Uuid(fileId, pageable)
        return shares.map { it.toDTO() }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun createFileShare(fileShareDTO: FileShareDTO): FileShareDTO {

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions
        if(getCurrentUserPermission(fileShareDTO.fileID, userDetails) != PERMISSION_LEVEL.READ_WRITE) {
            log.warn("User {} does not have WRITE permission for file {}", userDetails.id, fileShareDTO.fileID)
            throw ForbiddenException("Access denied: insufficient permissions to share file ${fileShareDTO.fileID}")
        }

        //calls User Service to verify that user exists
        getUserFromUserService(token, fileShareDTO.userID)

        val file = fileRepository.findById(fileShareDTO.fileID)
            .orElseThrow { ResourceNotFoundException("File not found: ${fileShareDTO.fileID}") }

        val share = FileShare(
            id = null,
            file = file,
            userID = fileShareDTO.userID,
            permission = fileShareDTO.permission,
            timestamp = Date()
        )
        return fileShareRepository.save(share).toDTO()

    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getFilesSharedWithCurrentUser( pageable: Pageable): Page<FileDTO> {

        // Get user ID directly from JWT in SecurityContext
        val userId = getUserIdFromJwt()
        log.debug("Current user ID: {}", userId)

        val filesPage = fileShareRepository.findByUserID(userId, pageable)

        // Raccoglie tutti gli owner ID distinti
        val ownerIds = filesPage.content.map { it.file.ownerId }.distinct()

        // Batch request per ottenere i nomi degli owner
        val token = authService.getAccessToken()
        val ownerNamesMap = getUsersBatchFromUserService(token, ownerIds)

        return filesPage.map {
            val ownerName = ownerNamesMap[it.file.ownerId]?.fullName
            it.file.toDTO(ownerName)
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun deleteFileShare(fileId: UUID, shareId: Long) {

        val token= authService.getAccessToken()
        val userDetails = getUserDetails(token)
        log.debug("Current user details: {}", userDetails)

        // Check permissions
        val file = fileRepository.findById(fileId)
            .orElseThrow { ResourceNotFoundException("File not found: $fileId") }

        // only owner can delete shares
        if (file.ownerId != userDetails.id) {
            throw ForbiddenException("Access denied: only file owner can delete share for file $fileId")
        }

        val share: FileShare = fileShareRepository.findById(shareId)
            .orElseThrow { ResourceNotFoundException("File share not found: $shareId") }

        if (share.file.uuid != file.uuid) {
            throw ResourceNotFoundException("File share $shareId does not belong to file $fileId")
        }
        fileShareRepository.delete(share)

    }


    override fun composeObject(upload: Upload) {
        try {

            val versionId = upload.fileVersionId
                ?: throw ResourceNotFoundException("FileVersion id not set for upload ${upload.id}")
            val fileVersion = fileVersionRepository.findById(versionId)
                .orElseThrow {
                    ResourceNotFoundException("FileVersion $versionId not found for upload ${upload.id}")
                }

            tusRepository.save(upload.apply { this.chunkCount = 1 })
            tusRepository.flush()

            val finalObjectKey = "${upload.fileId}/v${fileVersion.version}/${fileVersion.filename}"
            storageService.composeObject(upload, finalObjectKey)

            // aggiorno anche l'objectKey della versione
            fileVersion.objectKey = finalObjectKey
            fileVersion.status = FileStatus.AVAILABLE
            fileVersionRepository.save(fileVersion)
        } catch (e: Exception) {
            log.error("Errore durante composeObject", e)
            throw e
        }
    }


    override fun composeObjectClientSide(upload: Upload) {
        try {
            var chunkCounter = 1
            var newPartCounter = 0
            val minPartSize = 5242880L // 5 MiB

            do {
                //gets minimum chunks for which the sum is 5 MiB or more
                //5 MiB is default but dimension can be passed as parameter
                val byteArrays = storageService.getMinimumChunksForSizeWithIndex(upload.id.toString(), chunkCounter)
                log.debug("Merging {} chunks for upload {}", byteArrays.size, upload.id)

                val startIndex = chunkCounter  // Index di partenza per la cancellazione
                chunkCounter += byteArrays.size

                val totalSize = byteArrays.sumOf { it.size }
                newPartCounter += 1

                // Crea lista degli oggetti da eliminare PRIMA di creare il nuovo
                val objectsToDelete = (startIndex until chunkCounter).map { index ->
                    "${upload.id}/part-$index"
                }
                log.debug("Deleting from part {} to part {} for upload {}", startIndex, chunkCounter, upload.id)
                storageService.deleteObjects("uploads",objectsToDelete)

                //compatta
                val composedChunk = mergeByteArrays(byteArrays)
                val chunkDTO = ChunkDTO(
                    uuid = upload.id.toString(),
                    number = newPartCounter,  //the new object keys will start from 1 again
                    inputStream = composedChunk,
                    size = totalSize.toLong(),
                    contentType = upload.contentType,
                    filename = upload.filename,
                    bucket="uploads"
                )
                log.debug("Creating composed object {} in MINIO for upload {}", chunkDTO.number, upload.id)
                storageService.putObject(chunkDTO)
            } while (chunkCounter < upload.chunkCount)

            // Se l'ultima parte creata è < 5MB e ci sono più di 1 parte,
            // devo unire le ultime due parti
            if (newPartCounter > 1) {
                // Verifica la dimensione dell'ultima parte
                val lastPartKey = "${upload.id}/part-$newPartCounter"
                val lastPartSize = try {
                    storageService.getObjectSize("uploads", lastPartKey)
                } catch (e: Exception) {
                    log.warn("Could not get size of last part, assuming it's valid")
                    minPartSize // Assume it's valid
                }

                if (lastPartSize < minPartSize) {
                    log.debug("Last part is too small ({}), merging with previous part", lastPartSize)
                    // Scarica le ultime due parti
                    val prevPartKey = "${upload.id}/part-${newPartCounter - 1}"
                    val prevPartBytes = storageService.getObject("uploads", prevPartKey)
                    val lastPartBytes = storageService.getObject("uploads", lastPartKey)

                    // Calcola la dimensione totale prima di unire
                    val mergedSize = prevPartBytes.size + lastPartBytes.size

                    // Elimina entrambe le parti prima di creare quella nuova
                    storageService.deleteObjects("uploads", listOf(prevPartKey, lastPartKey))

                    // Uniscile
                    val merged = mergeByteArrays(listOf(prevPartBytes, lastPartBytes))

                    // Crea la parte unita con il numero della penultima
                    val mergedDTO = ChunkDTO(
                        uuid = upload.id.toString(),
                        number = newPartCounter - 1,
                        inputStream = merged,
                        size = mergedSize.toLong(),
                        contentType = upload.contentType,
                        filename = upload.filename,
                        bucket = "uploads"
                    )
                    storageService.putObject(mergedDTO)


                    newPartCounter -= 1
                    log.debug("Merged last two parts, now have {} parts", newPartCounter)
                }
            }

            upload.chunkCount = newPartCounter
            tusRepository.save(upload)

        } catch (e: Exception) {
            log.error("Error composing client-side object for upload ${upload.id}", e)
            throw StorageException("Error composing object for upload ${upload.id}", e)
        }
    }

    override fun mergeObject(upload: Upload){
        if (upload.mergeMode == MergeMode.CLIENT_SIDE){
            composeObjectClientSide(upload)
        }

        // Dopo composeObjectClientSide, verifica se il numero di chunk è gestibile
        // Se c'è un solo chunk, spostalo direttamente senza compose
        if (upload.chunkCount == 1) {
            log.debug("Only one chunk remaining after client-side compose, moving directly")
            moveSingleChunkUpload(upload)
            storageService.deletePartialObjects(upload.id.toString())
            return
        }

        composeObject(upload)

        storageService.deletePartialObjects(upload.id.toString())
    }

    override fun moveSingleChunkUpload(upload: Upload){
        val chunkBytes = storageService.getObject("uploads","${upload.id}/part-1")

        val versionId = upload.fileVersionId
            ?: throw ResourceNotFoundException("FileVersion id not set for upload ${upload.id}")
        val fileVersion = fileVersionRepository.findById(versionId)
            .orElseThrow {
                ResourceNotFoundException("FileVersion $versionId not found for upload ${upload.id}")
            }

        val finalObjectKey = "${upload.fileId}/v${fileVersion.version}/${fileVersion.filename}"

        val mergedChunk = ChunkDTO(
            uuid = upload.fileId.toString(),
            number = 1,
            inputStream = chunkBytes.inputStream(),
            size = upload.size!!,
            contentType = upload.contentType!!,
            filename = upload.filename,
            bucket = "files",
            version = fileVersion.version
        )
        storageService.putFinalObject( mergedChunk)

        // aggiorno objectKey e stato su FileVersion
        fileVersion.objectKey = finalObjectKey
        fileVersion.status = FileStatus.AVAILABLE
        fileVersionRepository.save(fileVersion)

        storageService.deletePartialObjects(upload.id.toString())
    }

    override fun getUserDetails(token:String): UserDTO {
        // Get user ID directly from JWT in SecurityContext
        val userId = getUserIdFromJwt()
        log.debug("Current user ID: {}", userId)

        return getUserFromUserService(token, userId)

    }

    private fun getUserFromUserService(token: String, userId: UUID): UserDTO {

        try {
            val userDTO = userServiceClient.get()
                .uri("/api/v1/users/{id}", userId)
                .headers { it.setBearerAuth(token) }
                .retrieve()
                .body(UserDTO::class.java)
                ?: throw ResourceNotFoundException("User not found: $userId")
            return userDTO
        } catch (ex: RestClientResponseException) {
            log.error("Error calling user service for user {}: {}", userId, ex.message)
            if (ex.statusCode.value() == 404) {
                throw ResourceNotFoundException("User not found: $userId")
            }
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service error: ${ex.statusText}")
        }
    }

    private fun getUsersBatchFromUserService(token: String, userIds: List<UUID>): Map<UUID, UserDTO> {
        if (userIds.isEmpty()) {
            return emptyMap()
        }

        try {
            val responseType = object : org.springframework.core.ParameterizedTypeReference<Map<UUID, UserDTO>>() {}
            val usersMap = userServiceClient.post()
                .uri("/api/v1/users/batch")
                .headers { it.setBearerAuth(token) }
                .body(userIds)
                .retrieve()
                .body(responseType)
                ?: emptyMap()

            log.debug("Fetched {} users from batch request", usersMap.size)
            return usersMap
        } catch (ex: RestClientResponseException) {
            log.error("Error calling user service batch endpoint: {}", ex.message)
            throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "User service error: ${ex.statusText}")
        }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getFileVersions(fileId: UUID): List<FileVersionDTO> {
        log.debug("Getting versions for file: $fileId")

        val file = fileRepository.findById(fileId)
            .orElseThrow { ResourceNotFoundException("File not found: $fileId") }

        // Check if user has permission to access this file
        val token = authService.getAccessToken()
        val userDTO = getUserDetails(token)
        getCurrentUserPermission(fileId, userDTO) // This will throw if no permission

        // Return all versions sorted by version number descending (newest first)
        return file.versions
            .sortedByDescending { it.version }
            .map { it.toDTO() }
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun downloadFileVersion(fileId: UUID, version: Long): DownloadDTO {
        log.debug("Downloading file: $fileId version: $version")

        val file = fileRepository.findById(fileId)
            .orElseThrow { ResourceNotFoundException("File not found: $fileId") }

        // Check if user has permission to access this file
        val token = authService.getAccessToken()
        val userDTO = getUserDetails(token)
        getCurrentUserPermission(fileId, userDTO) // This will throw if no permission

        // Find the specific version
        val fileVersion = file.versions.find { it.version == version }
            ?: throw ResourceNotFoundException("Version $version not found for file $fileId")

        // Check if version is available
        if (fileVersion.status != FileStatus.AVAILABLE) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "File version is not available for download (status: ${fileVersion.status})")
        }

        // Download from storage - getObject returns ByteArray
        val bytes = storageService.getObject("files", fileVersion.objectKey)

        return DownloadDTO(
            filename = fileVersion.filename,
            contentType = fileVersion.contentType,
            file = bytes
        )
    }
}