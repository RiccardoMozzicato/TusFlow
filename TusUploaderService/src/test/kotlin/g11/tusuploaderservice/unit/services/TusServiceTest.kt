package g11.tusuploaderservice.unit.services

import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.services.AuthService
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.exceptions.*
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.TusRepository
import g11.tusuploaderservice.services.FileServiceInterface
import g11.tusuploaderservice.services.StorageServiceInterface
import g11.tusuploaderservice.services.TusService
import io.micrometer.tracing.Tracer
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.util.ReflectionTestUtils
import java.security.MessageDigest
import java.util.*

@ExtendWith(MockKExtension::class)
class TusServiceTest {

    @MockK(relaxed = true)
    lateinit var tusRepository: TusRepository

    @MockK(relaxed = true)
    lateinit var fileRepository: FileRepository

    @MockK(relaxed = true)
    lateinit var storageService: StorageServiceInterface

    @MockK(relaxed = true)
    lateinit var fileService: FileServiceInterface

    @MockK(relaxed = true)
    lateinit var authService: AuthService

    @MockK(relaxed = true)
    lateinit var kafkaTemplate: KafkaTemplate<String, String>

    lateinit var tusService: TusService

    private val userId = UUID.randomUUID()

    @BeforeEach
    fun setup() {
        // Manually create TusService with mocks
        tusService = TusService(
            storageService = storageService,
            fileService = fileService,
            authService = authService,
            kafkaTemplate = kafkaTemplate,
            fileRepo = fileRepository,
            repo = tusRepository
        )

        // Set private fields using reflection
        ReflectionTestUtils.setField(tusService, "tusMaxSize", "10485760") // 10MB
        ReflectionTestUtils.setField(tusService, "expirationPeriod", 3600L) // 1 hour

        every { authService.getAccessToken() } returns "token"
        every { fileService.getUserDetails(any()) } returns UserDTO(
            id = userId,
            fullName = "Test User",
            email = "test@example.com",
            organization = "TestOrg",
            department = "TestDept",
            role = UserRole.ROLE_staff
        )
        every { fileService.getCurrentUserPermission(any(), any()) } returns PERMISSION_LEVEL.READ_WRITE
        every { fileRepository.findById(any()) } returns Optional.empty()
    }

    private fun makeUploadDTO(
        id: UUID? = null,
        fileUuid: UUID = UUID.randomUUID(),
        uploadLength: Long? = 1000L,
        uploadOffset: Long = 0L,
        uploadDeferLength: Int? = null,
        contentType: String? = "text/plain",
        filename: String? = "test.txt",
        uploadMetadata: Map<String, String>? = null
    ): UploadDTO {
        return UploadDTO(
            id = id,
            fileUuid = fileUuid,
            filename = filename,
            contentType = contentType,
            uploadLength = uploadLength,
            uploadOffset = uploadOffset,
            uploadDeferLength = uploadDeferLength,
            uploadMetadata = uploadMetadata
        )
    }

    private fun makeUpload(
        id: UUID = UUID.randomUUID(),
        fileUuid: UUID = UUID.randomUUID(),
        size: Long? = 1000L,
        totalOffset: Long = 0L,
        status: UploadStatus = UploadStatus.PENDING,
        chunkCount: Int = 0,
        ownerId: UUID = this.userId,
        organization: String = "TestOrg",
        department: String? = "TestDept",
        sharingScope: SharingScope = SharingScope.PERSONAL
    ): Upload {
        return Upload(
            id = id,
            fileId = fileUuid,
            ownerId = ownerId,
            filename = "test.txt",
            contentType = "text/plain",
            size = size,
            totalOffset = totalOffset,
            metadata = mapOf("key" to "value"),
            checksum = null,
            status = status,
            createdAt = Date(),
            expiresAt = Date(System.currentTimeMillis() + 3600000),
            objectKey = "$id/",
            mergeMode = MergeMode.SERVER_SIDE,
            chunkCount = chunkCount,
            fileVersionId = null,
            sharingScope = sharingScope,
            organization = organization,
            department = department
        )
    }

    private fun makeFile(
        uuid: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        organization: String = "TestOrg",
        department: String? = "TestDept",
        sharingScope: SharingScope = SharingScope.PERSONAL
    ): File {
        return File(
            uuid = uuid,
            ownerId = ownerId,
            createdAt = Date(),
            lastUpdateAt = Date(),
            lastFilename = "test.txt",
            currentVersion = 1L,
            organization = organization,
            department = department,
            sharingScope = sharingScope
        )
    }

    @Test
    fun `createUpload saves and returns upload entity with correct expiration`() {
        // arrange
        val uploadDTO = makeUploadDTO()
        every { tusRepository.existsByFileId(uploadDTO.fileUuid!!) } returns false
        every { fileRepository.findById(uploadDTO.fileUuid!!) } returns Optional.empty()
        every { tusRepository.save(any()) } answers {
            val upload = firstArg<Upload>()
            upload.id = UUID.randomUUID()
            upload
        }

        // act
        val result = tusService.createUpload(uploadDTO)

        // assert
        assertNotNull(result.id)
        assertEquals(uploadDTO.fileUuid, result.fileUuid)
        assertEquals(0L, result.uploadOffset)
        assertNotNull(result.uploadExpires)
        verify { tusRepository.save(any()) }
    }

    @Test
    fun `createUpload throws UploadTooLargeException when size exceeds max`() {
        // arrange
        val uploadDTO = makeUploadDTO(uploadLength = 20_000_000L) // 20MB > 10MB max

        // act / assert
        val ex = assertThrows<UploadTooLargeException> {
            tusService.createUpload(uploadDTO)
        }
        assertTrue(ex.message.contains("exceeds maximum"))
    }

    @Test
    fun `createUpload throws MissingFieldException when both uploadLength and uploadDeferLength are null`() {
        // arrange
        val uploadDTO = makeUploadDTO(uploadLength = null, uploadDeferLength = null)

        // act / assert
        val ex = assertThrows<MissingFieldException> {
            tusService.createUpload(uploadDTO)
        }
        assertTrue(ex.message.contains("Upload-Length or Upload-Defer-Length"))
    }

    @Test
    fun `createUpload accepts uploadDeferLength when value is 1`() {
        // arrange
        // Note: uploadDeferLength must be exactly 1 according to Bean Validation (@Min(1L) @Max(1L))
        // In real scenarios, values different from 1 would be rejected by the controller before reaching the service
        val uploadDTO = makeUploadDTO(uploadLength = null, uploadDeferLength = 1)
        every { tusRepository.existsByFileId(uploadDTO.fileUuid!!) } returns false
        every { fileRepository.findById(uploadDTO.fileUuid!!) } returns Optional.empty()
        every { tusRepository.save(any()) } answers {
            val upload = firstArg<Upload>()
            upload.id = UUID.randomUUID()
            upload
        }

        // act
        val result = tusService.createUpload(uploadDTO)

        // assert
        assertNotNull(result.id)
        assertEquals(uploadDTO.fileUuid, result.fileUuid)
        assertEquals(0L, result.uploadOffset)
        verify { tusRepository.save(any()) }
    }


    @Test
    fun `createUpload extracts contentType from metadata when not in header`() {
        // arrange
        val metadata = mapOf("Content-Type" to "application/pdf", "filename" to "doc.pdf")
        val uploadDTO = makeUploadDTO(contentType = "application/offset+octet-stream", uploadMetadata = metadata)
        every { tusRepository.existsByFileId(uploadDTO.fileUuid!!) } returns false
        every { fileRepository.findById(uploadDTO.fileUuid!!) } returns Optional.empty()
        every { tusRepository.save(any()) } answers {
            val upload = firstArg<Upload>()
            upload.id = UUID.randomUUID()
            upload
        }

        // act
        tusService.createUpload(uploadDTO)

        // assert
        // Verify that the saved entity has contentType extracted from metadata
        // and metadata no longer contains Content-Type
        verify {
            tusRepository.save(match { upload ->
                upload.contentType == "application/pdf" &&
                upload.metadata?.containsKey("Content-Type") == false &&
                upload.metadata?.get("filename") == "doc.pdf"
            })
        }
    }

    @Test
    fun `appendChunk throws ResourceNotFoundException when upload not found`() {
        // arrange
        val uploadDTO = makeUploadDTO(id = UUID.randomUUID())
        every { tusRepository.findById(uploadDTO.id!!) } returns Optional.empty()

        // act / assert
        val ex = assertThrows<ResourceNotFoundException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("not found"))
    }

    @Test
    fun `appendChunk throws ResourceAlreadyExistsException when upload is MERGE_PENDING`() {
        // arrange
        val upload = makeUpload(status = UploadStatus.MERGE_PENDING)
        val uploadDTO = makeUploadDTO(id = upload.id)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<ResourceAlreadyExistsException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("already been completed"))
    }

    @Test
    fun `appendChunk throws ResourceAlreadyExistsException when upload is DELETED`() {
        // arrange
        val upload = makeUpload(status = UploadStatus.DELETED)
        val uploadDTO = makeUploadDTO(id = upload.id)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<ResourceAlreadyExistsException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("already been deleted"))
    }

    @Test
    fun `appendChunk throws TusOffsetException when offset mismatch`() {
        // arrange
        val upload = makeUpload(totalOffset = 100L, status = UploadStatus.UPLOADING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 50L)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<TusOffsetException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("Invalid offset"))
    }

    @Test
    fun `appendChunk throws UploadTooLargeException when chunk exceeds declared length`() {
        // arrange
        val upload = makeUpload(size = 100L, totalOffset = 95L, status = UploadStatus.UPLOADING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 95L, uploadLength = null)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<UploadTooLargeException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("exceeds expected length"))
    }

    @Test
    fun `appendChunk throws UploadLengthException when trying to set length twice`() {
        // arrange
        val upload = makeUpload(size = 100L, totalOffset = 50L, status = UploadStatus.UPLOADING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 50L, uploadLength = 200L)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<UploadLengthException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("already set"))
    }

    @Test
    fun `appendChunk throws ExpirationException when upload expired`() {
        // arrange
        val expiredDate = Date(System.currentTimeMillis() - 10000)
        val upload = makeUpload(status = UploadStatus.UPLOADING)
        upload.expiresAt = expiredDate
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = upload.totalOffset, uploadLength = null)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act / assert
        val ex = assertThrows<ExpirationException> {
            tusService.appendChunk(uploadDTO, ByteArray(10))
        }
        assertTrue(ex.message.contains("expired"))
    }

    @Test
    fun `appendChunk successfully uploads chunk and updates offset`() {
        // arrange
        val upload = makeUpload(size = 1000L, totalOffset = 0L, status = UploadStatus.PENDING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = null)
        val chunk = ByteArray(100)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        val result = tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertEquals(100L, result.uploadOffset)
        verify { storageService.putObject(any()) }
    }

    @Test
    fun `appendChunk transitions to MERGE_PENDING when upload complete`() {
        // arrange
        val upload = makeUpload(size = 100L, totalOffset = 90L, status = UploadStatus.UPLOADING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 90L, uploadLength = null)
        val chunk = ByteArray(10)
        val file = makeFile(upload.fileId)

        val fileVersion = mockk<FileVersion>(relaxed = true) {
            every { id } returns 1L
            every { version } returns 1L
            every { filename } returns "test.txt"
            every { contentType } returns "text/plain"
            every { metadata } returns mapOf("key" to "value")
        }

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""
        every { fileRepository.findById(upload.fileId) } returns Optional.empty()
        every { fileService.createFile(any(), any()) } returns file
        every { fileService.createFileVersion(any(), any()) } returns fileVersion

        // act
        val result = tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertEquals(100L, result.uploadOffset)
        verify { fileService.createFile(upload.fileId, upload) }
        verify { fileService.createFileVersion(upload, file) }
        verify { kafkaTemplate.send(any<String>(), any(), any()) }
    }

    @Test
    fun `appendChunk creates new version when file already exists`() {
        // arrange
        val upload = makeUpload(size = 100L, totalOffset = 90L, status = UploadStatus.UPLOADING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 90L, uploadLength = null)
        val chunk = ByteArray(10)
        val file = makeFile(upload.fileId)

        val fileVersion = mockk<FileVersion>(relaxed = true) {
            every { id } returns 2L
            every { version } returns 2L
            every { filename } returns "test.txt"
            every { contentType } returns "text/plain"
            every { metadata } returns mapOf("key" to "value")
        }

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""
        every { fileRepository.findById(upload.fileId) } returns Optional.of(file)
        every { fileService.createFileVersion(any(), any()) } returns fileVersion

        // act
        val result = tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertEquals(100L, result.uploadOffset)
        verify(exactly = 0) { fileService.createFile(any(), any()) }
        verify { fileService.createFileVersion(upload, file) }
        verify { kafkaTemplate.send(any<String>(), any(), any()) }
    }

    @Test
    fun `appendChunk switches to CLIENT_SIDE merge for small chunks`() {
        // arrange
        val upload = makeUpload(size = 10000L, totalOffset = 0L, status = UploadStatus.PENDING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = null)
        val smallChunk = ByteArray(1000) // < 5MB

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        tusService.appendChunk(uploadDTO, smallChunk)

        // assert
        assertEquals(MergeMode.CLIENT_SIDE, upload.mergeMode)
    }

    @Test
    fun `appendChunk sets uploadLength when provided in deferred upload`() {
        // arrange
        val upload = makeUpload(size = null, totalOffset = 0L, status = UploadStatus.PENDING)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = 1000L)
        val chunk = ByteArray(100)

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        val result = tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertEquals(1000L, upload.size)
        assertEquals(100L, result.uploadOffset)
    }

    @Test
    fun `createUploadWithChunk creates upload and appends chunk`() {
        // arrange
        val uploadDTO = makeUploadDTO()
        val chunk = ByteArray(100)
        val createdUpload = makeUpload()

        every { tusRepository.existsByFileId(uploadDTO.fileUuid!!) } returns false
        every { fileRepository.findById(uploadDTO.fileUuid!!) } returns Optional.empty()
        every { tusRepository.save(any()) } answers {
            val upload = firstArg<Upload>()
            upload.id = createdUpload.id
            upload
        }
        every { tusRepository.findById(createdUpload.id!!) } returns Optional.of(createdUpload)
        every { storageService.putObject(any()) } returns ""

        // act
        val result = tusService.createUploadWithChunk(uploadDTO, chunk)

        // assert
        assertNotNull(result.id)
        verify { tusRepository.save(any()) }
        verify { storageService.putObject(any()) }
    }

    @Test
    fun `getUploadInfo returns upload DTO when found`() {
        // arrange
        val upload = makeUpload()
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act
        val result = tusService.getUploadInfo(upload.id.toString())

        // assert
        assertEquals(upload.id, result.id)
        assertEquals(upload.fileId, result.fileUuid)
        verify { tusRepository.findById(upload.id!!) }
    }

    @Test
    fun `getUploadInfo throws ResourceNotFoundException when not found`() {
        // arrange
        val id = UUID.randomUUID()
        every { tusRepository.findById(id) } returns Optional.empty()

        // act / assert
        val ex = assertThrows<ResourceNotFoundException> {
            tusService.getUploadInfo(id.toString())
        }
        assertTrue(ex.message.contains("does not exists"))
    }

    @Test
    fun `terminateUpload sets status to DELETED`() {
        // arrange
        val upload = makeUpload(status = UploadStatus.UPLOADING)
        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)

        // act
        tusService.terminateUpload(upload.id.toString())

        // assert
        assertEquals(UploadStatus.DELETED, upload.status)
    }

    @Test
    fun `terminateUpload throws ResourceNotFoundException when not found`() {
        // arrange
        val id = UUID.randomUUID()
        every { tusRepository.findById(id) } returns Optional.empty()

        // act / assert
        val ex = assertThrows<ResourceNotFoundException> {
            tusService.terminateUpload(id.toString())
        }
        assertTrue(ex.message.contains("does not exists"))
    }

    @Test
    fun `executeChecksum validates correct checksum`() {
        // arrange
        val content = "test content".toByteArray()
        val algorithm = "sha-256"
        val hash = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(content)
        )
        val checksumHeader = "$algorithm $hash"

        // act / assert - should not throw
        assertDoesNotThrow {
            tusService.executeChecksum(content, checksumHeader)
        }
    }

    @Test
    fun `executeChecksum throws ChecksumMismatchException for invalid checksum`() {
        // arrange
        val content = "test content".toByteArray()
        val checksumHeader = "sha-256 invalidhash123"

        // act / assert
        assertThrows<ChecksumMismatchException> {
            tusService.executeChecksum(content, checksumHeader)
        }
    }

    @Test
    fun `executeChecksum throws IllegalArgumentException for invalid header format`() {
        val content = "test content".toByteArray()
        val invalidHeaders = listOf("", "sha1", "sha1 abc def", "   ")
        for (header in invalidHeaders) {
            val ex = assertThrows<IllegalArgumentException> {
                tusService.executeChecksum(content, header)
            }
            assertTrue(ex.message?.contains("Invalid Upload-Checksum header format") == true)
        }
    }

    @Test
    fun `executeChecksum throws HashAlgorithmNotSupportedException for unknown algorithm`() {
        val content = "test content".toByteArray()
        val fakeHash = Base64.getEncoder().encodeToString("fakehash".toByteArray())
        val header = "notarealalgo $fakeHash"
        val ex = assertThrows<HashAlgorithmNotSupportedException> {
            tusService.executeChecksum(content, header)
        }
        assertTrue(ex.message.contains("not supported"))
    }

    @Test
    fun `executeChecksum throws ChecksumMismatchException for wrong hash`() {
        val content = "test content".toByteArray()
        val wrongHash = Base64.getEncoder().encodeToString("wronghash".toByteArray())
        val header = "md5 $wrongHash"
        val ex = assertThrows<ChecksumMismatchException> {
            tusService.executeChecksum(content, header)
        }
        assertTrue(ex.message.contains("Checksum mismatch"))
    }

    @Test
    fun `getSupportedHashAlgorithms returns known algorithms`() {
        val algos = tusService.getSupportedHashAlgorithms()
        // Almeno uno tra md5, sha1, sha256 deve essere presente
        assertTrue(algos.any { it in listOf("md5", "sha1", "sha256") }, "Should contain at least one known digest algorithm")
    }

    // Test disabled: getNewExpirationDate is a private method
    // @Test
    // fun `getNewExpirationDate adds configured period to instant`() {
    //     // arrange
    //     val now = Instant.now()
    //     val expectedExpiration = now.plusSeconds(3600L)
    //
    //     // act
    //     val result = tusService.getNewExpirationDate(now)
    //
    //     // assert
    //     assertTrue(result.isAfter(now))
    //     assertEquals(expectedExpiration.epochSecond, result.epochSecond)
    // }

    @Test
    fun `appendChunk detects content type when null`() {
        // arrange
        val upload = makeUpload(size = 1000L, totalOffset = 0L, status = UploadStatus.PENDING)
        upload.contentType = null
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = null)
        val textChunk = "Hello World".toByteArray()

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        tusService.appendChunk(uploadDTO, textChunk)

        // assert
        assertNotNull(upload.contentType)
    }

    @Test
    fun `appendChunk increments chunk count correctly`() {
        // arrange
        val upload = makeUpload(size = 1000L, totalOffset = 0L, status = UploadStatus.PENDING, chunkCount = 0)
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = null)
        val chunk = ByteArray(100)

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertEquals(1, upload.chunkCount)
    }

    @Test
    fun `appendChunk updates expiration date on each chunk`() {
        // arrange
        val oldExpiration = Date(System.currentTimeMillis() + 1000000)
        val upload = makeUpload(size = 1000L, totalOffset = 0L, status = UploadStatus.UPLOADING)
        upload.expiresAt = oldExpiration
        val uploadDTO = makeUploadDTO(id = upload.id, uploadOffset = 0L, uploadLength = null)
        val chunk = ByteArray(100)

        every { tusRepository.findById(upload.id!!) } returns Optional.of(upload)
        every { storageService.putObject(any()) } returns ""

        // act
        tusService.appendChunk(uploadDTO, chunk)

        // assert
        assertTrue(upload.expiresAt.after(oldExpiration))
    }
}
