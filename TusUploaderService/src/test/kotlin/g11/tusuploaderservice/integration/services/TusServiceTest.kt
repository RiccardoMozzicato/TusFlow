package g11.tusuploaderservice.integration.services


import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UploadDTO
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.entities.MergeMode
import g11.tusuploaderservice.entities.PERMISSION_LEVEL
import g11.tusuploaderservice.entities.Upload
import g11.tusuploaderservice.entities.UploadStatus
import g11.tusuploaderservice.exceptions.*
import g11.tusuploaderservice.integration.IntegrationTest
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.FileVersionRepository
import g11.tusuploaderservice.repositories.FilesOutboxEventsRepository
import g11.tusuploaderservice.repositories.TusRepository
import g11.tusuploaderservice.services.AuthService
import g11.tusuploaderservice.services.FileService
import g11.tusuploaderservice.services.TusService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.test.context.support.WithMockUser
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import java.util.Date
import org.mockito.kotlin.any
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.transaction.annotation.Transactional

class TusServiceTest : IntegrationTest() {
    @Autowired
    private lateinit var tusService: TusService
    @Autowired
    private lateinit var tusRepository: TusRepository
    @Autowired
    private lateinit var fileRepository: FileRepository
    @Autowired
    private lateinit var fileVersionRepository: FileVersionRepository
    @Autowired
    private lateinit var filesOutboxEventsRepository: FilesOutboxEventsRepository

    @MockitoBean
    private lateinit var authService: AuthService

    @MockitoBean
    private lateinit var fileService: FileService

    private fun setupJwtAuthentication(
        userId: UUID = UUID.randomUUID(),
        roles: List<String> = listOf("admin"),
        fullName: String = "Test User",
        email: String = "test@example.com",
        organization: String = "TestOrg",
        department: String? = "TestDept",
        userRole: UserRole = UserRole.ROLE_admin,
        storageQuota: Long? = null,
        storageUsed: Long = 0L
    ) {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId.toString())
            .claim("realm_access", mapOf("roles" to roles))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        val authentication = JwtAuthenticationToken(jwt, authorities)
        SecurityContextHolder.getContext().authentication = authentication

        // Mock AuthService per restituire un token finto
        `when`(authService.getAccessToken()).thenReturn("mock-test-token")

        // Mock FileService per restituire i dettagli utente forniti
        `when`(fileService.getUserDetails(any())).thenReturn(
            UserDTO(
                id = userId,
                fullName = fullName,
                email = email,
                organization = organization,
                department = department,
                role = userRole,
                storageQuota = storageQuota,
                storageUsed = storageUsed
            )
        )

        // Mock per i permessi dell'utente
        `when`(fileService.getCurrentUserPermission(any(), any())).thenReturn(PERMISSION_LEVEL.READ_WRITE)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create UploadDTO without completing it, so status should be PENDING`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Verifica che il database sia vuoto prima del test
        val countBefore = tusRepository.count()
        println("Number of uploads in DB before test: $countBefore")

        val generatedUploadDTO = tusService.createUpload(
            UploadDTO(
                uploadLength = 10000L,
                filename = "testfile.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("testfile".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Verifica che il record sia stato salvato nel database
        val countAfter = tusRepository.count()
        println("Number of uploads in DB after test: $countAfter")
        assertEquals(countBefore + 1, countAfter, "Upload should be saved in the database")

        // Verifica che il record esista nel repository
        val uploadInDb = tusRepository.findById(generatedUploadDTO.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in the database")
        println("Upload found in DB: ${uploadInDb.get()}")

        // Verifica tramite il service
        val uploadInformation = tusService.getUploadInfo(generatedUploadDTO.id.toString())
        assertEquals("testfile.txt", uploadInformation.filename, "Filename should match")
        assertEquals(fileUuid, uploadInformation.fileUuid, "File UUID should match")
        assertEquals(10000L, uploadInformation.uploadLength, "Upload length should match")

        // Verifica diretta dei campi dell'entità nel DB
        val uploadEntity = uploadInDb.get()
        assertEquals("testfile.txt", uploadEntity.filename, "Filename in DB should match")
        assertEquals(10000L, uploadEntity.size, "Upload length in DB should match")
        assertEquals(0L, uploadEntity.totalOffset, "Upload offset should be 0")
        assertEquals(UploadStatus.PENDING, uploadEntity.status, "Status should be PENDING")

        println("✓ Test passed: Upload saved correctly in testcontainer database")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create UploadDTO with size exceeding tusMaxSize should throw UploadTooLargeException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val tusMaxSize = 1073741824L // 1 GB (from application.properties)
        val tooLargeSize = tusMaxSize + 1 // Supera il limite di 1 byte

        // Verifica che venga lanciata l'eccezione corretta
        val exception = assertThrows(UploadTooLargeException::class.java) {
            tusService.createUpload(
                UploadDTO(
                    uploadLength = tooLargeSize,
                    filename = "large_file.txt",
                    fileUuid = fileUuid,
                    checksum = MessageDigest.getInstance("MD5").digest("largefile".toByteArray())
                        .joinToString("") { "%02x".format(it) },
                    sharingScope = SharingScope.PERSONAL
                )
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message.contains("File size exceeds maximum allowed of $tusMaxSize bytes"),
            "Exception message should mention the max size limit"
        )

        // Verifica che nessun record sia stato salvato nel database
        val uploadsInDb = tusRepository.findAll()
        val uploadForFile = uploadsInDb.find { it.fileId == fileUuid }
        assertNull(uploadForFile, "No upload should be saved when size exceeds limit")

        println("✓ Test passed: UploadTooLargeException thrown correctly for oversized upload")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create UploadDTO with null uploadLength and deferLength`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Verifica che venga lanciata un'eccezione quando uploadLength è null
        val exception = assertThrows(Exception::class.java) {
            tusService.createUpload(
                UploadDTO(
                    filename = "testfile.txt",
                    fileUuid = fileUuid,
                    checksum = MessageDigest.getInstance("MD5").digest("testfile".toByteArray())
                        .joinToString("") { "%02x".format(it) },
                    sharingScope = SharingScope.PERSONAL
                )
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        println("Exception type: ${exception::class.simpleName}")

        // Verifica che nessun record sia stato salvato nel database
        val uploadsInDb = tusRepository.findAll()
        val uploadForFile = uploadsInDb.find { it.fileId == fileUuid }
        assertNull(uploadForFile, "No upload should be saved when uploadLength is null")

        println("✓ Test passed: Exception thrown correctly when uploadLength is null")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create UploadDTO with custom contentType should use the provided contentType`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val customContentType = "image/png"

        // Crea un upload con un contentType personalizzato
        val generatedUploadDTO = tusService.createUpload(
            UploadDTO(
                uploadLength = 5000L,
                filename = "image.png",
                fileUuid = fileUuid,
                contentType = customContentType,
                checksum = MessageDigest.getInstance("MD5").digest("imagedata".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Verifica che il contentType sia stato impostato correttamente
        //assertNotNull(generatedUploadDTO.contentType, "ContentType should not be null")
        //assertEquals(customContentType, generatedUploadDTO.contentType, "ContentType should match the provided one")

        // Verifica nel database
        val uploadInDb = tusRepository.findById(generatedUploadDTO.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in the database")

        val uploadEntity = uploadInDb.get()
        assertEquals(customContentType, uploadEntity.contentType, "ContentType in DB should match")
        assertEquals("image.png", uploadEntity.filename, "Filename should match")
        assertEquals(5000L, uploadEntity.size, "Upload length should match")

        println("✓ Test passed: Custom contentType correctly set (line 68 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create UploadDTO with contentType in uploadMetadata should extract and use it`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val metadataContentType = "application/pdf"

        // Crea un upload con contentType in uploadMetadata
        val generatedUploadDTO = tusService.createUpload(
            UploadDTO(
                uploadLength = 8000L,
                filename = "document.pdf",
                fileUuid = fileUuid,
                contentType = null, // contentType è null, quindi verrà cercato in uploadMetadata
                uploadMetadata = mapOf("Content-Type" to metadataContentType, "author" to "John Doe"),
                checksum = MessageDigest.getInstance("MD5").digest("pdfdata".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Verifica che Content-Type sia stato rimosso da uploadMetadata
        assertFalse(
            generatedUploadDTO.uploadMetadata?.containsKey("Content-Type") ?: false,
            "Content-Type should be removed from uploadMetadata after extraction"
        )

        // Verifica che gli altri metadata siano ancora presenti
        assertTrue(
            generatedUploadDTO.uploadMetadata?.containsKey("author") ?: false,
            "Other metadata should still be present"
        )
        assertEquals("John Doe", generatedUploadDTO.uploadMetadata?.get("author"), "Author metadata should match")

        // Verifica nel database
        val uploadInDb = tusRepository.findById(generatedUploadDTO.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in the database")

        val uploadEntity = uploadInDb.get()
        assertEquals(metadataContentType, uploadEntity.contentType, "ContentType in DB should match")
        assertEquals("document.pdf", uploadEntity.filename, "Filename should match")
        assertEquals(8000L, uploadEntity.size, "Upload length should match")

        println("✓ Test passed: ContentType extracted from uploadMetadata (lines 70-71 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create upload with chunk should create upload and append chunk in one operation`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Mock per createFile e createFileVersion necessari quando l'upload viene completato
        `when`(fileService.createFile(any(), any())).thenAnswer { invocation ->
            val fileId = invocation.getArgument<UUID>(0)
            val upload = invocation.getArgument<Upload>(1)
            File(
                uuid = fileId,
                ownerId = upload.ownerId,
                createdAt = Date.from(Instant.now()),
                lastUpdateAt = Date.from(Instant.now()),
                lastFilename = upload.filename ?: "unknown",
                currentVersion = 1L,
                organization = upload.organization,
                department = upload.department,
                sharingScope = upload.sharingScope
            )
        }

        `when`(fileService.createFileVersion(any(), any())).thenAnswer { invocation ->
            val upload = invocation.getArgument<Upload>(0)
            val file = invocation.getArgument<File>(1)
            FileVersion(
                id = null,
                version = file.currentVersion,
                status = FileStatus.AVAILABLE,
                file = file,
                filename = upload.filename ?: "unknown",
                size = upload.size ?: 0L,
                metadata = upload.metadata,
                checksum = upload.checksum,
                contentType = upload.contentType ?: "application/octet-stream",
                createdAt = Date.from(Instant.now()),
                lastUpdateAt = Date.from(Instant.now()),
                objectKey = "${upload.id}/"
            )
        }

        val testContent = "This is test file content for upload with chunk".toByteArray()
        val uploadLength = testContent.size.toLong()

        // Crea un upload con chunk in un'unica operazione
        val generatedUploadDTO = tusService.createUploadWithChunk(
            UploadDTO(
                uploadLength = uploadLength,
                filename = "test_with_chunk.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("testwithchunk".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            ),
            testContent
        )

        // Verifica che l'upload sia stato creato
        assertNotNull(generatedUploadDTO.id, "Upload ID should not be null")

        // Verifica che il chunk sia stato caricato
        assertEquals(uploadLength, generatedUploadDTO.uploadOffset, "Upload offset should equal content size")

        // Verifica nel database
        val uploadInDb = tusRepository.findById(generatedUploadDTO.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in the database")

        val uploadEntity = uploadInDb.get()
        assertEquals("test_with_chunk.txt", uploadEntity.filename, "Filename should match")
        assertEquals(uploadLength, uploadEntity.size, "Upload length should match")
        assertEquals(uploadLength, uploadEntity.totalOffset, "Total offset should match upload length")
        assertEquals(UploadStatus.MERGE_PENDING, uploadEntity.status, "Status in DB should be MERGE_PENDING")
        assertEquals(1, uploadEntity.chunkCount, "Chunk count should be 1")

        println("✓ Test passed: createUploadWithChunk executed correctly (line 111 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk to non-existent upload should throw ResourceNotFoundException`() {
        setupJwtAuthentication()
        val nonExistentId = UUID.randomUUID()
        val testContent = "This is test content".toByteArray()

        // Verifica che l'upload non esista nel database
        val uploadInDb = tusRepository.findById(nonExistentId)
        assertFalse(uploadInDb.isPresent, "Upload should not exist in the database")

        // Tenta di appendere un chunk a un upload inesistente
        val exception = assertThrows(ResourceNotFoundException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = nonExistentId,
                    uploadOffset = 0L
                ),
                testContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("Resource not found: $nonExistentId"),
            "Exception message should contain the upload ID"
        )

        println("✓ Test passed: ResourceNotFoundException thrown for non-existent upload (line 121 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk to already completed upload should throw ResourceAlreadyExistsException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Mock per createFile e createFileVersion necessari quando l'upload viene completato
        `when`(fileService.createFile(any(), any())).thenAnswer { invocation ->
            val fileId = invocation.getArgument<UUID>(0)
            val upload = invocation.getArgument<Upload>(1)
            File(
                uuid = fileId,
                ownerId = upload.ownerId,
                createdAt = Date.from(Instant.now()),
                lastUpdateAt = Date.from(Instant.now()),
                lastFilename = upload.filename ?: "unknown",
                currentVersion = 1L,
                organization = upload.organization,
                department = upload.department,
                sharingScope = upload.sharingScope
            )
        }

        `when`(fileService.createFileVersion(any(), any())).thenAnswer { invocation ->
            val upload = invocation.getArgument<Upload>(0)
            val file = invocation.getArgument<File>(1)
            FileVersion(
                id = null,
                version = file.currentVersion,
                status = FileStatus.AVAILABLE,
                file = file,
                filename = upload.filename ?: "unknown",
                size = upload.size ?: 0L,
                metadata = upload.metadata,
                checksum = upload.checksum,
                contentType = upload.contentType ?: "application/octet-stream",
                createdAt = Date.from(Instant.now()),
                lastUpdateAt = Date.from(Instant.now()),
                objectKey = "${upload.id}/"
            )
        }

        val testContent = "Small test content".toByteArray()
        val uploadLength = testContent.size.toLong()

        // Crea e completa un upload in un'unica operazione
        val completedUpload = tusService.createUploadWithChunk(
            UploadDTO(
                uploadLength = uploadLength,
                filename = "completed_file.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("completedfile".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            ),
            testContent
        )

        // Verifica che l'upload sia completato
        val uploadInDb = tusRepository.findById(completedUpload.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in the database")
        assertEquals(UploadStatus.MERGE_PENDING, uploadInDb.get().status, "Upload should be in MERGE_PENDING status")

        // Tenta di appendere un altro chunk all'upload già completato
        val additionalContent = "More content".toByteArray()
        val exception = assertThrows(ResourceAlreadyExistsException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = completedUpload.id,
                    uploadOffset = uploadLength
                ),
                additionalContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("The upload has already been completed"),
            "Exception message should indicate upload is already completed"
        )

        println("✓ Test passed: ResourceAlreadyExistsException thrown for already completed upload (line 131 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk with invalid offset should throw TusOffsetException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Crea un upload
        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = 10000L,
                filename = "offset_test.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("offsettest".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Verifica che l'offset corrente sia 0
        assertEquals(0L, upload.uploadOffset, "Initial offset should be 0")

        // Tenta di appendere un chunk con un offset errato (diverso da 0)
        val testContent = "Test content".toByteArray()
        val wrongOffset = 100L

        val exception = assertThrows(TusOffsetException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = upload.id,
                    uploadOffset = wrongOffset
                ),
                testContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("Invalid offset"),
            "Exception message should indicate invalid offset"
        )
        assertTrue(
            exception.message!!.contains("Provided: $wrongOffset"),
            "Exception message should contain provided offset"
        )
        assertTrue(
            exception.message!!.contains("Actual offset: 0"),
            "Exception message should contain actual offset"
        )

        println("✓ Test passed: TusOffsetException thrown for invalid offset (line 139 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk exceeding uploadLength should throw UploadTooLargeException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val uploadLength = 100L

        // Crea un upload con lunghezza specifica
        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = uploadLength,
                filename = "size_test.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("sizetest".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Tenta di caricare un chunk più grande della lunghezza totale prevista
        val tooLargeContent = ByteArray(150) { 'A'.code.toByte() }

        val exception = assertThrows(UploadTooLargeException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = upload.id,
                    uploadOffset = 0L
                ),
                tooLargeContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("File length exceeds expected length"),
            "Exception message should indicate length exceeded"
        )

        println("✓ Test passed: UploadTooLargeException thrown for chunk exceeding uploadLength (line 145 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk with uploadDeferLength when size already set should throw UploadLengthException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val uploadLength = 5000L

        // Crea un upload con lunghezza già impostata
        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = uploadLength,
                filename = "defer_test.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("defertest".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Verifica che la size sia impostata nel database
        val uploadInDb = tusRepository.findById(upload.id!!)
        assertTrue(uploadInDb.isPresent, "Upload should exist in database")
        assertNotNull(uploadInDb.get().size, "Size should be set")
        assertEquals(uploadLength, uploadInDb.get().size, "Size should match uploadLength")

        // Tenta di appendere un chunk con uploadDeferLength quando size è già impostata
        val testContent = "Test content".toByteArray()

        val exception = assertThrows(UploadLengthException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = upload.id,
                    uploadOffset = 0L,
                    uploadDeferLength = 1
                ),
                testContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("Upload length is already set, cannot defer length"),
            "Exception message should indicate length already set"
        )

        println("✓ Test passed: UploadLengthException thrown when trying to defer length with size already set (line 152 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk to expired upload should throw ExpirationException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Crea un upload
        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = 5000L,
                filename = "expired_test.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("expiredtest".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Modifica manualmente la data di scadenza nel database per renderla scaduta
        val uploadEntity = tusRepository.findById(upload.id!!).get()
        uploadEntity.expiresAt = Date.from(Instant.now().minusSeconds(3600)) // Scaduto 1 ora fa
        tusRepository.save(uploadEntity)

        // Verifica che l'upload sia scaduto
        val updatedUpload = tusRepository.findById(upload.id!!).get()
        assertTrue(
            updatedUpload.expiresAt.toInstant().isBefore(Instant.now()),
            "Upload should be expired"
        )

        // Tenta di appendere un chunk a un upload scaduto
        val testContent = "Test content".toByteArray()

        val exception = assertThrows(ExpirationException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = upload.id,
                    uploadOffset = 0L
                ),
                testContent
            )
        }

        // Verifica il messaggio dell'eccezione
        println("Exception message: ${exception.message}")
        assertTrue(
            exception.message!!.contains("Upload has expired"),
            "Exception message should indicate upload expired"
        )

        println("✓ Test passed: ExpirationException thrown for expired upload (line 156 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append chunk when upload length provided but size already set should throw UploadLengthException`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()
        val uploadLength = 1000L

        // Crea un upload con size già impostata
        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = uploadLength,
                filename = "size_already_set.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("sizeset".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Tenta di inviare un append con uploadLength non null ma la size è già presente
        val smallContent = "ok".toByteArray()

        val exception = assertThrows(UploadLengthException::class.java) {
            tusService.appendChunk(
                UploadDTO(
                    id = upload.id,
                    uploadOffset = 0L,
                    uploadLength = uploadLength
                ),
                smallContent
            )
        }

        println("Exception message: ${exception.message}")
        assertTrue(exception.message!!.contains("File size already set"), "Exception message should indicate file size already set")

        println("✓ Test passed: UploadLengthException thrown when uploadLength provided but size already set (line 147 covered)")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Append small chunk should switch merge mode to CLIENT_SIDE`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication()

        // Crea un upload con deferLength (size non impostata)
        val upload = tusService.createUpload(
            UploadDTO(
                uploadDeferLength = 1,
                filename = "client_merge.txt",
                fileUuid = fileUuid,
                checksum = MessageDigest.getInstance("MD5").digest("clientmerge".toByteArray())
                    .joinToString("") { "%02x".format(it) },
                sharingScope = SharingScope.PERSONAL
            )
        )

        // Chunk piccolo < 5MB
        val smallChunk = ByteArray(1024) { 0 }

        val result = tusService.appendChunk(
            UploadDTO(
                id = upload.id,
                uploadOffset = 0L
            ),
            smallChunk
        )

        // Verifica nel DB che il mergeMode sia CLIENT_SIDE e che lo status sia UPLOADING
        val uploadEntity = tusRepository.findById(result.id!!).get()
        assertEquals(MergeMode.CLIENT_SIDE, uploadEntity.mergeMode, "Merge mode should be CLIENT_SIDE for small chunks")
        assertEquals(UploadStatus.UPLOADING, uploadEntity.status, "Status should be UPLOADING after partial small chunk")

        println("✓ Test passed: small chunk sets mergeMode to CLIENT_SIDE (line 169 covered)")
    }

    // region Storage quota

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create upload with quota persists a STORAGE_RESERVED outbox event`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication(storageQuota = 10_000_000L, storageUsed = 0L)

        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = 10_000L,
                filename = "quota_reserved.txt",
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL
            )
        )

        val reserved = filesOutboxEventsRepository.findAll().filter {
            it.eventType == "STORAGE_RESERVED" && it.payload["uploadId"] == upload.id.toString()
        }
        assertEquals(1, reserved.size, "Exactly one STORAGE_RESERVED event should be persisted")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create upload without quota still persists a STORAGE_RESERVED outbox event`() {
        // Usage must be tracked even with no quota, so a quota can be set later with the right value.
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication(storageQuota = null)

        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = 10_000L,
                filename = "no_quota_reserved.txt",
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL
            )
        )

        val reserved = filesOutboxEventsRepository.findAll().filter {
            it.eventType == "STORAGE_RESERVED" && it.payload["uploadId"] == upload.id.toString()
        }
        assertEquals(1, reserved.size, "STORAGE_RESERVED must be emitted even without a quota")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Create upload exceeding quota throws and persists nothing`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication(storageQuota = 5_000L, storageUsed = 0L)

        val exception = assertThrows(UploadTooLargeException::class.java) {
            tusService.createUpload(
                UploadDTO(
                    uploadLength = 6_000L, // above the 5_000 quota
                    filename = "over_quota.txt",
                    fileUuid = fileUuid,
                    sharingScope = SharingScope.PERSONAL
                )
            )
        }
        assertTrue(exception.message.contains("storage quota"))

        // The @Transactional service rolls back: neither the upload nor the outbox event survive.
        assertNull(tusRepository.findAll().find { it.fileId == fileUuid }, "No upload should be saved")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Terminate upload persists a STORAGE_RELEASED outbox event`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication(storageQuota = 10_000_000L, storageUsed = 0L)

        val upload = tusService.createUpload(
            UploadDTO(
                uploadLength = 10_000L,
                filename = "to_release.txt",
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL
            )
        )

        tusService.terminateUpload(upload.id.toString())

        val released = filesOutboxEventsRepository.findAll().filter {
            it.eventType == "STORAGE_RELEASED" && it.payload["uploadId"] == upload.id.toString()
        }
        assertEquals(1, released.size, "Exactly one STORAGE_RELEASED event should be persisted")
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `Deferred upload declaring length over quota is soft-deleted and chunks purged`() {
        val fileUuid = UUID.randomUUID()
        setupJwtAuthentication(storageQuota = 5_000L, storageUsed = 0L)

        // Deferred upload: size unknown at creation, so no reservation/quota check yet.
        val upload = tusService.createUpload(
            UploadDTO(
                uploadDeferLength = 1,
                filename = "deferred_over_quota.txt",
                fileUuid = fileUuid,
                sharingScope = SharingScope.PERSONAL
            )
        )

        // First chunk without a declared length: stored in MinIO, status becomes UPLOADING.
        val firstChunk = ByteArray(1024) { 0 }
        tusService.appendChunk(
            UploadDTO(id = upload.id, uploadOffset = 0L),
            firstChunk
        )
        assertEquals(UploadStatus.UPLOADING, tusRepository.findById(upload.id!!).get().status)

        // Second chunk declares the final length (6000) which exceeds the remaining quota (5000).
        val secondChunk = ByteArray(100) { 0 }
        val exception = assertThrows(UploadTooLargeException::class.java) {
            tusService.appendChunk(
                UploadDTO(id = upload.id, uploadOffset = 1024L, uploadLength = 6_000L),
                secondChunk
            )
        }
        assertTrue(exception.message.contains("storage quota"))

        // The cleanup runs in its own committed transaction, so the soft-delete survives the throw.
        val cleaned = tusRepository.findById(upload.id!!).get()
        assertEquals(UploadStatus.DELETED, cleaned.status, "Upload should be soft-deleted on quota rejection")

        // Nothing was reserved for this upload (deferred chunks never emit STORAGE_RESERVED).
        val reserved = filesOutboxEventsRepository.findAll().filter {
            it.eventType == "STORAGE_RESERVED" && it.payload["uploadId"] == upload.id.toString()
        }
        assertTrue(reserved.isEmpty(), "No STORAGE_RESERVED event should be persisted for an over-quota deferred upload")
    }

    // endregion
}
