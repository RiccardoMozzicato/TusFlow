package g11.tusuploaderservice.unit.services

import g11.tusuploaderservice.dto.*
import g11.tusuploaderservice.entities.*
import g11.tusuploaderservice.exceptions.ForbiddenException
import g11.tusuploaderservice.exceptions.ResourceNotFoundException
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.FileShareRepository
import g11.tusuploaderservice.repositories.FileVersionRepository
import g11.tusuploaderservice.repositories.FilesOutboxEventsRepository
import g11.tusuploaderservice.repositories.TusRepository
import g11.tusuploaderservice.services.AuthService
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.StorageServiceInterface
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.server.ResponseStatusException
import java.util.*

@ExtendWith(MockKExtension::class)
class FileServiceTest {

    @MockK(relaxed = true)
    lateinit var tusRepository: TusRepository

    @MockK(relaxed = true)
    lateinit var fileRepository: FileRepository

    @MockK(relaxed = true)
    lateinit var storageService: StorageServiceInterface

    @MockK(relaxed = true)
    lateinit var fileShareRepository: FileShareRepository

    @MockK(relaxed = true)
    lateinit var userServiceClient: RestClient

    @MockK(relaxed = true)
    lateinit var fileVersionRepository: FileVersionRepository

    @MockK(relaxed = true)
    lateinit var outboxRepository: FilesOutboxEventsRepository

    @MockK(relaxed = true)
    lateinit var authService: AuthService

    @MockK(relaxed = true)
    lateinit var tracer: io.micrometer.tracing.Tracer

    @InjectMockKs
    lateinit var fileService: FileService

    @MockK
    lateinit var uriSpec: RestClient.RequestHeadersUriSpec<*>

    private val userId = UUID.randomUUID()
    private val token = "test-token"

    @BeforeEach
    fun setUp() {
        mockkStatic("g11.tusuploaderservice.utils.UtilsKt")
        every { g11.tusuploaderservice.utils.getUserIdFromJwt() } returns userId
        every { authService.getAccessToken() } returns token
        every { userServiceClient.get() } returns uriSpec

        val auth = TestingAuthenticationToken("user", null, listOf(SimpleGrantedAuthority("ROLE_staff")))
        SecurityContextHolder.getContext().authentication = auth
    }

    @AfterEach
    fun tearDown() {
        unmockkStatic("g11.tusuploaderservice.utils.UtilsKt")
        SecurityContextHolder.clearContext()
    }

    private fun makeUpload(
        uuid: UUID = UUID.randomUUID(),
        ownerId: UUID = this.userId,
        organization: String = "TestOrg",
        department: String? = "TestDept",
        sharingScope: SharingScope = SharingScope.PERSONAL
    ): Upload {
        return Upload(
            id = uuid,
            fileId = uuid,
            ownerId = ownerId,
            filename = "file.txt",
            contentType = "text/plain",
            size = 100L,
            totalOffset = 100L,
            metadata = mapOf("k" to "v"),
            checksum = "ck",
            status = UploadStatus.MERGE_PENDING,
            createdAt = Date(),
            expiresAt = Date(System.currentTimeMillis() + 100000),
            objectKey = "obj",
            mergeMode = MergeMode.SERVER_SIDE,
            chunkCount = 1,
            fileVersionId = 1L,
            sharingScope = sharingScope,
            organization = organization,
            department = department
        )
    }

    private fun makeFile(
        uuid: UUID = UUID.randomUUID(),
        ownerId: UUID = this.userId,
        organization: String = "TestOrg",
        department: String? = "TestDept",
        sharingScope: SharingScope = SharingScope.PERSONAL
    ): File {
        return File(
            uuid = uuid,
            ownerId = ownerId,
            createdAt = Date(),
            lastUpdateAt = Date(),
            lastFilename = "file.txt",
            currentVersion = 1L,
            organization = organization,
            department = department,
            sharingScope = sharingScope
        )
    }

    private fun mockUserServiceOk(userId: UUID) {
        mockUserAs(userId, "TestOrg", "TestDept", UserRole.ROLE_staff)
    }

    private fun mockUserAs(
        userId: UUID,
        organization: String,
        department: String,
        role: UserRole,
    ) {
        val headersSpec = mockk<RestClient.RequestHeadersSpec<*>>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        every { uriSpec.uri("/api/v1/users/{id}", match<UUID> { it == userId }) } returns headersSpec
        every { headersSpec.headers(any()) } returns headersSpec
        every { headersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(UserDTO::class.java) } returns UserDTO(
            id = userId,
            fullName = "Test User",
            email = "test@example.com",
            organization = organization,
            department = department,
            role = role,
        )
    }

    private fun mockUserServiceError(userId: UUID, status: Int) {
        val headersSpec = mockk<RestClient.RequestHeadersSpec<*>>()

        every { uriSpec.uri("/api/v1/users/{id}", match<UUID> { it == userId }) } returns headersSpec
        every { headersSpec.headers(any()) } returns headersSpec
        every { headersSpec.retrieve() } throws RestClientResponseException(
            "Error", status, "Status Text", null, null, null
        )
    }

    @Test
    fun `createFile saves and returns file entity`() {
        val upload = makeUpload()
        every { fileRepository.save(any()) } answers { firstArg() }

        val result = fileService.createFile(upload.fileId, upload)

        assertEquals(upload.fileId, result.uuid)
        verify { fileRepository.save(match { it.uuid == upload.fileId }) }
    }

    @Test
    fun `createFileVersion adds first version without incrementing currentVersion beyond 1`() {
        val upload = makeUpload()
        val file = makeFile()
        val previousVersion = file.currentVersion
        every { fileRepository.save(any()) } answers { firstArg() }
        every { tusRepository.save(any()) } answers { firstArg() }

        fileService.createFileVersion(upload, file)

        assertEquals(previousVersion, file.currentVersion)
        assertEquals(1, file.versions.size)
        assertEquals(1L, file.versions.first().version)
        verify { fileRepository.save(file) }
        verify { tusRepository.save(upload) }
    }

    @Test
    fun `getFileMetadata returns dto when file exists`() {
        val file = makeFile()
        mockUserServiceOk(userId)
        every { fileRepository.findById(file.uuid) } returns Optional.of(file)

        val dto = fileService.getFileMetadata(file.uuid)

        assertEquals(file.uuid, dto.id)
        verify { fileRepository.findById(file.uuid) }
    }

    @Test
    fun `getFileMetadata throws when not found`() {
        val id = UUID.randomUUID()
        mockUserServiceOk(userId)
        every { fileRepository.findById(id) } returns Optional.empty()

        val ex = assertThrows<ResourceNotFoundException> {
            fileService.getFileMetadata(id)
        }
        assertTrue(ex.message!!.contains(id.toString()))
    }

    @Test
    fun `getFileMetadata denies access to organization file for user from different organization`() {
        val otherUserId = UUID.randomUUID()
        every { g11.tusuploaderservice.utils.getUserIdFromJwt() } returns otherUserId
        val file = makeFile(sharingScope = SharingScope.ORGANIZATION)
        mockUserAs(otherUserId, "OtherOrg", "TestDept", UserRole.ROLE_staff)
        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.findByFile_UuidAndUserID(file.uuid, otherUserId) } returns null

        assertThrows<ForbiddenException> {
            fileService.getFileMetadata(file.uuid)
        }
    }

    @Test
    fun `getFileMetadata denies access to department file for user from different organization`() {
        val otherUserId = UUID.randomUUID()
        every { g11.tusuploaderservice.utils.getUserIdFromJwt() } returns otherUserId
        val file = makeFile(sharingScope = SharingScope.DEPARTMENT)
        mockUserAs(otherUserId, "OtherOrg", "TestDept", UserRole.ROLE_staff)
        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.findByFile_UuidAndUserID(file.uuid, otherUserId) } returns null

        assertThrows<ForbiddenException> {
            fileService.getFileMetadata(file.uuid)
        }
    }

    @Test
    fun `getFileMetadata allows owner to access their own personal file`() {
        val file = makeFile(sharingScope = SharingScope.PERSONAL)
        mockUserServiceOk(userId)
        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.findByFile_UuidAndUserID(file.uuid, userId) } returns null

        val dto = fileService.getFileMetadata(file.uuid)

        assertEquals(file.uuid, dto.id)
    }

    @Test
    fun `getFileMetadata throws when security context has no JWT (unauthenticated)`() {
        every { g11.tusuploaderservice.utils.getUserIdFromJwt() } throws
            IllegalStateException("No JWT authentication found in security context")

        assertThrows<IllegalStateException> {
            fileService.getFileMetadata(UUID.randomUUID())
        }

        verify(exactly = 0) { fileRepository.findById(any()) }
    }

    @Test
    fun `getFileMetadata fails for AI client because service accounts are not registered in user service`() {
        val aiClientId = UUID.randomUUID()
        every { g11.tusuploaderservice.utils.getUserIdFromJwt() } returns aiClientId
        mockUserServiceError(aiClientId, 404)

        assertThrows<ResourceNotFoundException> {
            fileService.getFileMetadata(UUID.randomUUID())
        }

        verify(exactly = 0) { fileRepository.findById(any()) }
    }

    @Test
    fun `deleteFile deletes file and all versions when file exists`() {
        val id = UUID.randomUUID()
        val file = makeFile(id)
        mockUserServiceOk(userId)
        every { fileRepository.findById(id) } returns Optional.of(file)
        every { fileRepository.deleteById(id) } just Runs
        every { storageService.deleteAllVersions(id.toString()) } just Runs

        fileService.deleteFile(id)

        verify { fileRepository.deleteById(id); storageService.deleteAllVersions(id.toString()) }
    }

    @Test
    fun `getFileShares maps page content to DTOs`() {
        val file = makeFile()
        mockUserServiceOk(userId)
        val share = FileShare(
            id = 1L,
            userID = UUID.randomUUID(),
            permission = PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date(),
            file = file
        )

        val page = PageImpl(listOf(share), PageRequest.of(0, 10), 1)
        every { fileRepository.existsById(file.uuid) } returns true
        every { fileRepository.findById(file.uuid) } returns Optional.of(file) // Needed for permission check
        every { fileShareRepository.findByFile_Uuid(file.uuid, any()) } returns page

        val result = fileService.getFileShares(file.uuid, PageRequest.of(0, 10))

        assertEquals(1, result.totalElements)
        assertEquals(share.id, result.content[0].id)
        verify { fileRepository.existsById(file.uuid) }
        verify { fileShareRepository.findByFile_Uuid(file.uuid, any()) }
    }

    @Test
    fun `deleteFileShare deletes share when belongs to file`() {
        val file = makeFile()
        mockUserServiceOk(userId)
        val share = FileShare(
            id = 2L,
            userID = UUID.randomUUID(),
            permission = PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date(),
            file = file
        )

        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.findById(share.id!!) } returns Optional.of(share)
        every { fileShareRepository.delete(share) } just Runs

        fileService.deleteFileShare(file.uuid, share.id!!)

        verify {
            fileRepository.findById(file.uuid)
            fileShareRepository.findById(share.id!!)
            fileShareRepository.delete(share)
        }
    }

    @Test
    fun `deleteFileShare throws when share does not belong to file`() {
        val file = makeFile()
        val otherFile = makeFile()
        mockUserServiceOk(userId)
        val share = FileShare(
            id = 3L,
            userID = UUID.randomUUID(),
            permission = PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date(),
            file = otherFile
        )

        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.findById(share.id!!) } returns Optional.of(share)

        val ex = assertThrows<ResourceNotFoundException> {
            fileService.deleteFileShare(file.uuid, share.id!!)
        }
        assertTrue(ex.message!!.contains("does not belong"))
    }

    private fun mockUsersBatch(users: Map<UUID, UserDTO>) {
        val postUriSpec = mockk<RestClient.RequestBodyUriSpec>()
        val responseSpec = mockk<RestClient.ResponseSpec>()

        every { userServiceClient.post() } returns postUriSpec
        every { postUriSpec.uri("/api/v1/users/batch") } returns postUriSpec
        every { postUriSpec.headers(any()) } returns postUriSpec
        every { postUriSpec.body(any<List<UUID>>()) } returns postUriSpec
        every { postUriSpec.retrieve() } returns responseSpec
        every {
            responseSpec.body(any<org.springframework.core.ParameterizedTypeReference<Map<UUID, UserDTO>>>())
        } returns users
    }

    private fun makeFilters(sharingScopes: Set<SharingScope> = setOf(SharingScope.PERSONAL)) =
        FiltersDTO(null, null, null, "createdAt", Sort.Direction.DESC, 0u, 10u, sharingScopes)

    @Test
    fun `getFiles returns empty page when no files match`() {
        mockUserServiceOk(userId)
        every { fileRepository.findAll(any(), any<Pageable>()) } returns PageImpl(emptyList(), PageRequest.of(0,10), 0)

        val result = fileService.getFiles(makeFilters())

        assertEquals(0, result.totalElements)
        assertTrue(result.content.isEmpty())
    }

    @Test
    fun `getFiles throws IllegalArgumentException when no sharing scope provided`() {
        mockUserServiceOk(userId)

        assertThrows<IllegalArgumentException> { fileService.getFiles(makeFilters(emptySet())) }

        verify(exactly = 0) { fileRepository.findAll(any(), any<Pageable>()) }
    }

    @Test
    fun `getFiles returns mapped page with owner names`() {
        mockUserServiceOk(userId)
        val file = makeFile()
        mockUsersBatch(
            mapOf(
                userId to UserDTO(
                    id = userId,
                    fullName = "Test User",
                    email = "test@example.com",
                    organization = "TestOrg",
                    department = "TestDept",
                    role = UserRole.ROLE_staff,
                )
            )
        )
        every { fileRepository.findAll(any(), any<Pageable>()) } returns
            PageImpl(listOf(file), PageRequest.of(0, 10), 1)

        val result = fileService.getFiles(makeFilters())

        assertEquals(1, result.totalElements)
        assertEquals(file.uuid, result.content[0].id)
    }

    @Test
    fun `getFiles runs a single paginated query when multiple scopes requested`() {
        mockUserServiceOk(userId)
        val personalFile = makeFile(sharingScope = SharingScope.PERSONAL)
        val departmentFile = makeFile(sharingScope = SharingScope.DEPARTMENT)
        mockUsersBatch(emptyMap())
        every { fileRepository.findAll(any(), any<Pageable>()) } returns
            PageImpl(listOf(personalFile, departmentFile), PageRequest.of(0, 10), 2)

        val result = fileService.getFiles(
            makeFilters(setOf(SharingScope.PERSONAL, SharingScope.DEPARTMENT))
        )

        assertEquals(2, result.totalElements)
        verify(exactly = 1) { fileRepository.findAll(any(), any<Pageable>()) }
    }

    @Test
    fun `updateFileMetadata updates filename and merges metadata`() {
        val file = makeFile()
        mockUserServiceOk(userId)
        val fileVersion = FileVersion(
            id = 1L,
            version = 1L,
            status = FileStatus.MERGE_PENDING,
            file = file,
            filename = "old.txt",
            size = 10L,
            metadata = mapOf("a" to "1"),
            checksum = null,
            contentType = "text/plain",
            createdAt = Date(),
            lastUpdateAt = Date(),
            objectKey = "obj"
        )
        val fileId = fileVersion.file!!.uuid
        every { fileRepository.findById(fileId) } returns Optional.of(file) // For permission check
        every { fileVersionRepository.findByFileUuidAndVersion(fileId, 1L) } returns Optional.of(fileVersion)

        fileService.updateFileMetadata(fileId, 1L, mapOf("filename" to "new.txt", "b" to "2"))

        assertEquals("new.txt", fileVersion.filename)
        assertEquals("1", fileVersion.metadata!!["a"])
        assertEquals("2", fileVersion.metadata!!["b"])
        verify { fileVersionRepository.findByFileUuidAndVersion(fileId, 1L) }
    }

    @Test
    fun `updateFileMetadata throws when version not found`() {
        val id = UUID.randomUUID()
        val file = makeFile(id)
        mockUserServiceOk(userId)
        every { fileRepository.findById(id) } returns Optional.of(file) // For permission check
        every { fileVersionRepository.findByFileUuidAndVersion(id, 1L) } returns Optional.empty()

        assertThrows<ResourceNotFoundException> { fileService.updateFileMetadata(id, 1L, mapOf("k" to "v")) }
    }

    @Test
    fun `createFileShare succeeds`() {
        val targetUserId = UUID.randomUUID()
        val file = makeFile()
        mockUserServiceOk(userId)
        mockUserServiceOk(targetUserId)
        every { fileRepository.findById(file.uuid) } returns Optional.of(file)
        every { fileShareRepository.save(any()) } answers { firstArg() }
        val dto = FileShareDTO(null, file.uuid, targetUserId, PERMISSION_LEVEL.READ_ONLY, Date().toString())

        val saved = fileService.createFileShare(dto)

        assertEquals(file.uuid, saved.fileID)
        assertEquals(targetUserId, saved.userID)
        verify { fileShareRepository.save(any()) }
    }

    @Test
    fun `createFileShare throws ResourceNotFound when user not found`() {
        val targetUserId = UUID.randomUUID()
        mockUserServiceOk(userId)
        mockUserServiceError(targetUserId, 404)
        val file = makeFile()
        every { fileRepository.findById(file.uuid) } returns Optional.of(file) // For permission check

        val dto = FileShareDTO(null, file.uuid, targetUserId, PERMISSION_LEVEL.READ_ONLY, Date().toString())

        val ex = assertThrows<ResourceNotFoundException> { fileService.createFileShare(dto) }
        assertTrue(ex.message!!.contains("User not found"))
    }

    @Test
    fun `createFileShare throws ResponseStatusException on gateway error`() {
        val targetUserId = UUID.randomUUID()
        mockUserServiceOk(userId)
        mockUserServiceError(targetUserId, 500)
        val file = makeFile()
        every { fileRepository.findById(file.uuid) } returns Optional.of(file) // For permission check

        val dto = FileShareDTO(null, file.uuid, targetUserId, PERMISSION_LEVEL.READ_ONLY, Date().toString())

        val ex = assertThrows<ResponseStatusException> { fileService.createFileShare(dto) }
        assertEquals(HttpStatus.BAD_GATEWAY, ex.statusCode)
    }
}

