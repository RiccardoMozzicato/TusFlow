package g11.tusuploaderservice.services

import g11.tusuploaderservice.dto.ChunkDTO
import g11.tusuploaderservice.dto.FileUploadEvent
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.dto.additionalParameters
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.dto.toEntity
import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.entities.toDTO
import g11.tusuploaderservice.exceptions.ChecksumMismatchException
import g11.tusuploaderservice.exceptions.ExpirationException
import g11.tusuploaderservice.exceptions.ForbiddenException
import g11.tusuploaderservice.exceptions.GenericException
import g11.tusuploaderservice.exceptions.HashAlgorithmNotSupportedException
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.exceptions.TusOffsetException
import g11.tusuploaderservice.exceptions.MissingFieldException
import g11.tusuploaderservice.exceptions.ResourceAlreadyExistsException
import g11.tusuploaderservice.exceptions.StorageException
import g11.tusuploaderservice.exceptions.UploadLengthException
import g11.tusuploaderservice.exceptions.UploadTooLargeException
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.TusRepository
import jakarta.validation.Valid
import org.apache.tika.Tika
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.validation.annotation.Validated
import org.springframework.web.client.RestTemplate
import java.io.IOException
import java.util.UUID
import java.security.Security
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Base64
import java.util.Date
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.FilesOutboxEvent
import g11.tusuploaderservice.repositories.FilesOutboxEventsRepository
import io.micrometer.tracing.Tracer
import kotlin.let


@Service
@Validated
class TusService(
    private val storageService: StorageServiceInterface,
    private val fileService: FileServiceInterface,
    private val authService: AuthService,
    private val outboxRepository: FilesOutboxEventsRepository,
    private val fileRepo: FileRepository,
    private val repo: TusRepository,
    private val tracer: Tracer,
    @Lazy private val self: TusServiceInterface
): TusServiceInterface {
    @Value("\${tus.maxSize}") private lateinit var tusMaxSize:String
    @Value("\${tus.expirationPeriod}") private var expirationPeriod:Long = 0

    private val log = LoggerFactory.getLogger(TusService::class.java)
    private val restTemplate = RestTemplate()
    private val tika = Tika()

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin') and " +
            "(#upload.sharingScope.name() == 'DEPARTMENT' ? hasAnyRole('ROLE_manager','ROLE_admin') : true) and " +
            "(#upload.sharingScope.name() == 'ORGANIZATION' ? hasRole('ROLE_admin') : true)")
    override fun createUpload(@Valid upload: UploadDTO): UploadDTO {
        val token = authService.getAccessToken()
        var fileContentType: String? = null
        var storageOwnerId: UUID? = null

        log.debug("SERVICE - new upload: \n{}", upload.toString())
        //region Tus validation

        //if contentType is provided and is not the default TUS one, use it
        if( upload.contentType != null && !upload.contentType.startsWith("application/offset+octet-stream") )
            fileContentType = upload.contentType
        else if (upload.uploadMetadata != null && upload.uploadMetadata!!.containsKey("Content-Type")) {
            fileContentType = upload.uploadMetadata!!["Content-Type"]
            upload.uploadMetadata= upload.uploadMetadata!!.minus("Content-Type")
        }

        // Check upload length against max size
        if (upload.uploadLength != null && upload.uploadLength > tusMaxSize.toLong()) {
            throw UploadTooLargeException("File size exceeds maximum allowed of $tusMaxSize bytes")
        }

        // Either uploadLength or uploadDeferLength must be provided
        if (upload.uploadLength == null && upload.uploadDeferLength == null) {
            throw MissingFieldException("Either Upload-Length or Upload-Defer-Length must be provided")
        }

        //endregion

        val userDetails = fileService.getUserDetails(token)

        //region Access control
        val file = fileRepo.findById(upload.fileUuid!!) //fileUuid - Validated by TusController
        //check if file exists (new version) or not (new file)
        if (file.isPresent) {
            //file exists, check ownership

            //getCurrentUserPermission throws ForbiddenException if user has no access
            if(fileService.getCurrentUserPermission(upload.fileUuid, userDetails) != PERMISSION_LEVEL.READ_WRITE)
                throw ForbiddenException("User does not have permission to modify the file: ${upload.fileUuid}")
        }
        //if the file does not exist, the role validation is done by the PreAuthorize annotation

        //endregion

        //region Storage quota 
        if (upload.uploadLength != null) {
            val ownerDetails = if (file.isPresent) {
                if (fileService.isOwner(upload.fileUuid, userDetails)) {
                    userDetails
                } else {
                    fileService.getUserFromUserService(token, file.get().ownerId)
                }
            } else {
                userDetails
            }

            // Always track storage usage so a quota can be set later with the right
            // storageUsed value; only enforce the limit when a quota is actually set.
            storageOwnerId = ownerDetails.id
            if (ownerDetails.storageQuota != null &&
                upload.uploadLength > (ownerDetails.storageQuota - ownerDetails.storageUsed)) {
                log.debug("File size exceeds user storage quota of {} bytes", ownerDetails.storageQuota)
                throw UploadTooLargeException("File size exceeds user storage quota of ${ownerDetails.storageQuota} bytes")
            }
        }

        //endregion
        val expirationDate = getNewExpirationDate(Instant.now())
        val dto: UploadDTO = upload.copy(uploadExpires = expirationDate.toString(), uploadOffset = 0L, contentType = fileContentType)

        val param=additionalParameters(
            ZonedDateTime.now(ZoneOffset.UTC).toString(),
            "uploads",
            null,
            null,
            0,
            MergeMode.SERVER_SIDE,
            status = UploadStatus.PENDING,
            organization = userDetails.organization,
            department = userDetails.department, //can be null
            ownerId = userDetails.id
        )

        val uploadEntity: Upload
        try {
            log.debug("SERVICE - Creating new upload: \n{}", dto)
            uploadEntity = repo.save(dto.toEntity(param))
            uploadEntity.objectKey=uploadEntity.id.toString()+"/"

            if (upload.uploadLength != null && storageOwnerId != null) {
                val storageEvent = FilesOutboxEvent(
                    eventType = "STORAGE_RESERVED",
                    payload = mapOf(
                        "ownerUserId" to storageOwnerId.toString(),
                        "fileId" to uploadEntity.fileId.toString(),
                        "uploadId" to uploadEntity.id.toString(),
                        "size" to upload.uploadLength,
                        "traceparent" to tracer.currentSpan()?.context()
                            ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
                    ),
                    createdAt = Date.from(Instant.now())
                )
                outboxRepository.save(storageEvent)
                log.debug("Saved storage reserved outbox event for upload {}", uploadEntity.id)
            }
        } catch (e: IOException) {
            throw StorageException("Failed to save file metadata for resource: ${dto.id}", e)
        } catch (e: Exception) {
            throw GenericException("Unexpected error while creating resource: ${dto.id}", e)
        }

        return uploadEntity.toDTO()
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin') and " +
            "(#upload.sharingScope.name() == 'DEPARTMENT' ? hasAnyRole('ROLE_manager','ROLE_admin') : true) and " +
            "(#upload.sharingScope.name() == 'ORGANIZATION' ? hasRole('ROLE_admin') : true)")
    override fun createUploadWithChunk(@Valid upload: UploadDTO, content: ByteArray): UploadDTO {
        val createdUpload = createUpload(upload)
        return appendChunk(createdUpload.copy(uploadLength = null), content)
    }

    // Do NOT roll back on a quota rejection: appendChunk soft-deletes the upload (status=DELETED)
    // in this same transaction and must let that change commit while still surfacing the 413.
    @Transactional(noRollbackFor = [UploadTooLargeException::class])
    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun appendChunk(@Valid upload: UploadDTO, content: ByteArray): UploadDTO {
        val optUpload = repo.findById(upload.id!!) // ID - Validated by TusController

        log.debug("Service: Appending chunk to upload ID: ${upload.id}, Chunk size: ${content.size} bytes")
        if (optUpload.isEmpty)
            throw ResourceNotFoundException("Resource not found: ${upload.id}")
        val actualUpload = optUpload.get()

        //region Tus validation

        // Uploaded the whole file, is just waiting to be merged into the final object
        if (actualUpload.status == UploadStatus.MERGE_PENDING)
            throw ResourceAlreadyExistsException("The upload has already been completed")

        // Upload has been deleted
        if (actualUpload.status == UploadStatus.DELETED)
            throw ResourceAlreadyExistsException("The upload has already been deleted")

        val offset = upload.uploadOffset
        // Check offset, if not matching throw exception
        if (actualUpload.totalOffset != offset)
            throw TusOffsetException("Invalid offset. Provided: $offset. Actual offset: ${actualUpload.totalOffset}")

        // uploadLength is set then check if the new chunk is bigger than the expected length
        // uploadLength is set then if the size is already set cant be overwritten
        // uploadLength is set and no size, then set the size
        if (upload.uploadLength != null) {
            if (actualUpload.size != null) throw UploadLengthException("File size already set")
            if (actualUpload.totalOffset + content.size > upload.uploadLength) throw UploadTooLargeException("File length exceeds expected length")
            actualUpload.size = upload.uploadLength
        }

        if (actualUpload.size != null) {
            if (actualUpload.totalOffset + content.size > actualUpload.size!!) throw UploadTooLargeException("File length exceeds expected length")
        }

        // uploadDeferLength and size cannot be both set
        if (upload.uploadDeferLength != null && actualUpload.size != null)
            throw UploadLengthException("Upload length is already set, cannot defer length")

        // Check expiration
        if (isExpired(actualUpload.expiresAt)) {
            throw ExpirationException("Upload has expired")
        }
        //endregion

        //get file if exists (for access control and new file/version creation)
        val file = fileRepo.findById(actualUpload.fileId).orElse(null)

        log.debug("Asking for a token")
        //get userDetails from UserDetailService (calls API)
        val token = authService.getAccessToken()
        val userDetails = fileService.getUserDetails(token)

        log.debug("User details: $userDetails")
        //region Access Control

        // Check permissions based on whether file exists or not
        if (file == null) {
            // Upload for a new file - check if user can create files with this sharing scope
            log.debug("File not found, checking upload permission for new file creation")
            checkUploadPermission(actualUpload, userDetails)
            log.debug("Upload permission granted for new file creation")
        } else {
            // File exists - check if user has write permission on the file
            log.debug("File exists, checking user permissions on existing file")
            if (fileService.getCurrentUserPermission(actualUpload.fileId, userDetails) != PERMISSION_LEVEL.READ_WRITE) {
                throw ForbiddenException("User does not have permission to modify the file: ${actualUpload.fileId}")
            }
            log.debug("User has write permission on file")
        }
        //endregion

        //region Storage quota

        if (upload.uploadLength != null) {
            val ownerDetails = if (file == null || fileService.isOwner(file.uuid, userDetails)) {
                userDetails
            } else {
                fileService.getUserFromUserService(token, file.ownerId)
            }

            // Always track storage usage so a quota can be set later with the right
            // storageUsed value; only enforce the limit when a quota is actually set.
            if (ownerDetails.storageQuota != null &&
                upload.uploadLength > (ownerDetails.storageQuota - ownerDetails.storageUsed)) {
                log.debug("File size exceeds user storage quota of {} bytes", ownerDetails.storageQuota)
                // Soft-delete the upload and purge its chunks; the noRollbackFor on this method
                // lets that change commit even though the 413 below is thrown.
                self.softDeleteAndPurgeChunks(actualUpload.id!!)
                throw UploadTooLargeException("File size exceeds user storage quota of ${ownerDetails.storageQuota} bytes")
            }

            val storageEvent = FilesOutboxEvent(
                eventType = "STORAGE_RESERVED",
                payload = mapOf(
                    "ownerUserId" to ownerDetails.id.toString(),
                    "fileId" to actualUpload.fileId.toString(),
                    "uploadId" to actualUpload.id.toString(),
                    "size" to upload.uploadLength,
                    "traceparent" to tracer.currentSpan()?.context()
                        ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
                ),
                createdAt = Date.from(Instant.now())
            )
            outboxRepository.save(storageEvent)
            log.debug("Saved storage reserved outbox event for upload {}", actualUpload.id)
        }

        //endregion

        // Detect content type if not provided
        if (actualUpload.contentType.isNullOrBlank()) {
            actualUpload.contentType = tika.detect(content)
            log.debug("Content type detected: ${actualUpload.contentType}")
        }

        val newOffset = offset + content.size
        // Determine status, if size is known and with the new chunk we reach the size then set to MERGE_PENDING
        val status = if (actualUpload.size != null && newOffset == actualUpload.size)
            UploadStatus.MERGE_PENDING
        else
            UploadStatus.UPLOADING

        log.debug("Upload status: {}", status)
        // If the size is less than 5MB switch to client side merge
        if (content.size < 5242880 && status == UploadStatus.UPLOADING) {
            actualUpload.mergeMode = MergeMode.CLIENT_SIDE
        }

            log.debug("SERVICE - Uploading chunk: \n{}", actualUpload.toString())
            val now = Instant.now()
            val uploadEntity = actualUpload.let {
                it.totalOffset = newOffset
                it.size = it.size
                it.status = status
                it.chunkCount += 1
                it.expiresAt = Date.from(getNewExpirationDate(now))
                it.contentType
                it
            }

            var version: FileVersion? = null
            if (status == UploadStatus.MERGE_PENDING) {
                log.info("Upload ${uploadEntity.id} completed at $now")
                log.info("Creating file entry for upload ${uploadEntity.id}")

                if (file == null) {
                    log.debug("File not found, creating new file")
                    val newFile = fileService.createFile(uploadEntity.fileId, uploadEntity)
                    version = fileService.createFileVersion(uploadEntity, newFile)

                    log.info("Created file ${newFile.uuid} and the first version of it {}", version)
                } else {
                    log.debug("File already exists, creating new version")
                    version = fileService.createFileVersion(uploadEntity, file)
                }

                val ownerId = file?.ownerId ?: uploadEntity.ownerId
                val storageEvent = FilesOutboxEvent(
                    eventType = "STORAGE_CONFIRMED",
                    payload = mapOf(
                        "ownerUserId" to ownerId.toString(),
                        "fileId" to uploadEntity.fileId.toString(),
                        "fileVersionId" to version?.id?.toString(),
                        "uploadId" to uploadEntity.id.toString(),
                        "size" to uploadEntity.size,
                        "traceparent" to tracer.currentSpan()?.context()
                            ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
                    ),
                    createdAt = Date.from(Instant.now())
                )
                outboxRepository.save(storageEvent)
                log.debug("Saved storage confirmed outbox event for upload {}", uploadEntity.id)
            }

            // Create the chunkDTO for the storage into minio
            val chunkDTO = ChunkDTO(
                filename = if (uploadEntity.status == UploadStatus.MERGE_PENDING && uploadEntity.chunkCount == 1)
                    actualUpload.filename
                else
                    null,
                uuid = actualUpload.id.toString(),
                number = uploadEntity.chunkCount,
                size = content.size.toLong(),
                inputStream = content.inputStream(),
                contentType = actualUpload.contentType,
                bucket = "uploads"
            )

            log.debug("Creating object {} in MINIO", chunkDTO)
            storageService.putObject(chunkDTO)
            log.debug("uploadEntity after chunk upload: {}", uploadEntity.toDTO().toString())

            if (status == UploadStatus.MERGE_PENDING) {
                log.debug("Attempting to send Kafka message for upload {}", uploadEntity.id)
                if (version == null) {
                    log.error("File version creation failed for upload: {}", uploadEntity.id)
                    throw GenericException("File version creation failed for upload: ${uploadEntity.id}")
                }

                // Crea l'evento di upload come Map
                val fileUploadEvent = mapOf(
                    "fileId" to uploadEntity.fileId.toString(),
                    "fileVersionId" to version.id.toString(),
                    "version" to version.version,
                    "ownerUserId" to uploadEntity.ownerId.toString(),
                    "filename" to version.filename,
                    "contentType" to version.contentType,
                    "metadata" to version.metadata,
                    "traceparent" to tracer.currentSpan()?.context()
                        ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
                )


                // Salva evento nella tabella outbox per Kafka
                val outboxEvent = FilesOutboxEvent(
                    eventType = "FILE_INSERTED",
                    payload = fileUploadEvent,
                    createdAt = Date.from(Instant.now())
                )

                outboxRepository.save(outboxEvent)
                log.debug("Saved outbox event for upload {}", uploadEntity.id)
            }

            return uploadEntity.toDTO()
        }


    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun executeChecksum(chunk: ByteArray, checksumHeader: String) {
        val parts = checksumHeader.trim().split(" ")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid Upload-Checksum header format")
        }
        verifyHash(parts[1], chunk, parts[0].lowercase())
    }

    private fun calculateHash(content: ByteArray, algorithm: String): String {
        val digest: ByteArray = MessageDigest.getInstance(algorithm).digest(content)
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun verifyHash(receivedHash: String, content: ByteArray, algorithm: String): Boolean {
        val normalizedAlgorithm = algorithm.replace("-", "").lowercase()
        if(!getSupportedHashAlgorithms().contains(normalizedAlgorithm)) {
            throw HashAlgorithmNotSupportedException("Hash algorithm '$algorithm' is not supported")
        }else if(calculateHash(content, algorithm) != receivedHash){
            throw ChecksumMismatchException("Checksum mismatch")
        }
        return true
    }

    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun getSupportedHashAlgorithms(): List<String> {
        return Security.getAlgorithms("MessageDigest").toList().sorted().map { it.replace("-","").lowercase() }
    }

    private fun getNewExpirationDate(instant: Instant): Instant {
        //iso date instant
        return instant.plusSeconds(expirationPeriod)
    }


    private fun isExpired(expiresAt: Date): Boolean {
        val last = expiresAt.toInstant()
        return last.isBefore(Instant.now())
    }


    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun getUploadInfo(id:String): UploadDTO {
        val upload: Upload = repo.findById(UUID.fromString(id)).orElseThrow{
            ResourceNotFoundException("Resource does not exists: $id")
        }

        //region Access Control
        //calls UserDetailService to get user details
        val token = authService.getAccessToken()
        val userDetails = fileService.getUserDetails(token)

        val file = fileRepo.findById(upload.fileId).orElse(null)

        if(file == null) { //upload of a new file
            //throws ForbiddenException if user has no access
            checkUploadPermission(upload, userDetails)
        }
        else{
            //throws ForbiddenException if user has no access
            //READ_ONLY is enough to get upload info
            fileService.getCurrentUserPermission(upload.fileId, userDetails)
        }
        //endregion

        return upload.toDTO()
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_staff','ROLE_manager','ROLE_admin')")
    override fun terminateUpload(id: String){
        val upload: Upload = repo.findById(UUID.fromString(id))
            .orElseThrow{ ResourceNotFoundException("Resource does not exists: $id") }

        //region Access Control
        //calls UserDetailService to get user details
        val token = authService.getAccessToken()
        val userDetails = fileService.getUserDetails(token)

        val file = fileRepo.findById(upload.fileId).orElse(null)

        if(file == null) { //upload of a new file
            //throws ForbiddenException if user has no access
            checkUploadPermission(upload, userDetails)
        }
        else{
            //throws ForbiddenException if user has no access
            //READ_ONLY is enough to get upload info
            fileService.getCurrentUserPermission(upload.fileId, userDetails)
        }
        //endregion

        upload.status = UploadStatus.DELETED // JPA effettua l'aggiornamento in automatico

        if (upload.size != null) {
            val ownerId = file?.ownerId ?: upload.ownerId
            val storageEvent = FilesOutboxEvent(
                eventType = "STORAGE_RELEASED",
                payload = mapOf(
                    "ownerUserId" to ownerId.toString(),
                    "fileId" to upload.fileId.toString(),
                    "uploadId" to upload.id.toString(),
                    "size" to upload.size,
                    "traceparent" to tracer.currentSpan()?.context()
                        ?.let { "00-${it.traceId()}-${it.spanId()}-01" }
                ),
                createdAt = Date.from(Instant.now())
            )
            outboxRepository.save(storageEvent)
            log.debug("Saved storage released outbox event for upload {}", upload.id)
        }
    }

    /**
     * Soft-deletes the upload and purges its partial chunks from storage.
     *
     * Joins the caller's transaction; the caller (appendChunk) is annotated noRollbackFor the
     * quota exception, so this status change commits even though a 413 is thrown afterwards.
     */
    @Transactional
    override fun softDeleteAndPurgeChunks(uploadId: UUID) {
        val upload = repo.findById(uploadId).orElse(null) ?: return
        upload.status = UploadStatus.DELETED
        storageService.deletePartialObjects(uploadId.toString())
        log.debug("Upload {} soft-deleted and chunks purged after exceeding storage quota", uploadId)
    }

    private fun checkUploadPermission(upload: Upload, userDetails: UserDTO) {
        //this function must be called only if file does not exist yet, that is, it is the upload for a new file

        log.debug("Checking upload permission for user ${userDetails.id} on upload ${upload.id}")

        log.debug("Upload owner: ${upload.ownerId}, User ID: ${userDetails.id}")
                if (upload.ownerId != userDetails.id) {
                    throw ForbiddenException("User does not have permission to upload file chunks for another user")
                }

        log.debug("Upload organization: ${upload.organization}, User organization: ${userDetails.organization}")
                //these checks can throw only if user changed role/department/organization since the upload creation
                if (upload.sharingScope == SharingScope.DEPARTMENT) {
                    if (userDetails.role != UserRole.ROLE_manager && userDetails.role != UserRole.ROLE_admin) {
                        throw ForbiddenException("User does not have permission to create file in DEPARTMENT scope")
                    }
                } else if (upload.sharingScope == SharingScope.ORGANIZATION) {
                    if (userDetails.role != UserRole.ROLE_admin) {
                        throw ForbiddenException("User does not have permission to create file in ORGANIZATION scope")
                    }
                }

    }


}