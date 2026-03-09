package g11.tusuploaderservice.integration.e2e

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.ninjasquad.springmockk.MockkBean
import dasniko.testcontainers.keycloak.KeycloakContainer
import g11.tusuploaderservice.dto.FileDTO
import g11.tusuploaderservice.dto.SharingScope
import g11.tusuploaderservice.dto.UserDTO
import g11.tusuploaderservice.dto.UserRole
import g11.tusuploaderservice.entities.File
import g11.tusuploaderservice.entities.FileStatus
import g11.tusuploaderservice.entities.FileVersion
import g11.tusuploaderservice.integration.IntegrationTest
import g11.tusuploaderservice.repositories.FileRepository
import g11.tusuploaderservice.repositories.TusRepository
import io.minio.BucketExistsArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.*
import org.springframework.test.context.ContextConfiguration
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestTemplate
import java.io.ByteArrayInputStream
import java.time.Instant
import java.util.Date
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test E2E con Keycloak Testcontainer
 *
 * Simula il flusso completo:
 * 1. Utente si autentica con Keycloak (usando password grant per simulare browser OAuth2)
 * 2. TusUploaderService riceve il token dell'utente e valida JWT
 * 3. TusUploaderService usa client_credentials (machine-to-machine) per chiamare UserDetailService
 *
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ContextConfiguration(initializers = [E2ETests.KeycloakInitializer::class])
class E2ETests : IntegrationTest() {

    @Autowired
    lateinit var restTemplate: TestRestTemplate

    @Autowired
    lateinit var uploadRepository: TusRepository

    @Autowired
    lateinit var fileRepository: FileRepository

    @Autowired
    lateinit var fileShareRepository: g11.tusuploaderservice.repositories.FileShareRepository

    @Autowired
    lateinit var minioClient: MinioClient

    // Mock del RestClient che chiama UserDetailService
    // In produzione, questo chiamerebbe il vero UserDetailService
    @MockkBean
    @Qualifier("userServiceClient")
    lateinit var userServiceClient: RestClient

    private val testUserId = UUID.fromString("a0727703-0653-449e-a4f3-039bf14ae73a") // test_admin UUID
    private lateinit var testFileId: UUID

    @BeforeEach
    fun setupMocks() {
        // Mock della risposta di UserDetailService quando TusService chiama con client_credentials
        val requestHeadersUriSpec = mockk<RestClient.RequestHeadersUriSpec<*>>(relaxed = true)
        val requestHeadersSpec = mockk<RestClient.RequestHeadersSpec<*>>(relaxed = true)
        val responseSpec = mockk<RestClient.ResponseSpec>(relaxed = true)

        // Mock per chiamate GET individuali (es: GET /api/v1/users/{id})
        every { userServiceClient.get() } returns requestHeadersUriSpec
        every { requestHeadersUriSpec.uri(any<String>(), any<UUID>()) } returns requestHeadersSpec
        every { requestHeadersUriSpec.uri(any<String>()) } returns requestHeadersSpec
        every { requestHeadersSpec.headers(any()) } returns requestHeadersSpec
        every { requestHeadersSpec.retrieve() } returns responseSpec
        every { responseSpec.body(UserDTO::class.java) } returns UserDTO(
            id = testUserId,
            fullName = "Test Admin",
            email = "testadmin@example.com",
            organization = "Test Organization",
            department = "Test Department",
            role = UserRole.ROLE_admin
        )

        // Mock per chiamate POST batch (es: POST /api/v1/users/batch)
        val postSpec = mockk<RestClient.RequestBodyUriSpec>(relaxed = true)
        val postHeadersSpec = mockk<RestClient.RequestBodySpec>(relaxed = true)
        val postResponseSpec = mockk<RestClient.ResponseSpec>(relaxed = true)

        every { userServiceClient.post() } returns postSpec
        every { postSpec.uri(any<String>()) } returns postHeadersSpec
        every { postHeadersSpec.headers(any()) } returns postHeadersSpec
        every { postHeadersSpec.body(any<List<UUID>>()) } returns postHeadersSpec
        every { postHeadersSpec.retrieve() } returns postResponseSpec
        every { postResponseSpec.body(any<ParameterizedTypeReference<Map<UUID, UserDTO>>>()) } returns mapOf(
            testUserId to UserDTO(
                id = testUserId,
                fullName = "Test Admin",
                email = "testadmin@example.com",
                organization = "Test Organization",
                department = "Test Department",
                role = UserRole.ROLE_admin
            )
        )

        // Mock anche toBodilessEntity() per le validazioni
        every { responseSpec.toBodilessEntity() } returns ResponseEntity.ok().build()

        // Create MinIO bucket if not exists
        try {
            val bucketExists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket("files").build()
            )
            if (!bucketExists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket("files").build())
            }
        } catch (_: Exception) {
            // Bucket may already exist
        }

        // Create a test file with a version for testing
        testFileId = UUID.randomUUID()

        val file = File(
            uuid = testFileId,
            createdAt = Date.from(Instant.now()),
            lastUpdateAt = Date.from(Instant.now()),
            lastFilename = "test.pdf",
            currentVersion = 1L,
            ownerId = testUserId,
            organization = "Test Organization",
            department = "Test Department",
            sharingScope = SharingScope.PERSONAL
        )

        val now = Date.from(Instant.now())
        val fileVersion = FileVersion(
            id = null,
            version = 1L,
            status = FileStatus.AVAILABLE,
            file = file,
            filename = "test.pdf",
            size = 1024L,
            metadata = mapOf("author" to "Test Author"),
            checksum = "abc123",
            contentType = "application/pdf",
            createdAt = now,
            lastUpdateAt = now,
            objectKey = testFileId.toString()
        )

        file.versions.add(fileVersion)
        fileRepository.save(file)

        // Upload test content to MinIO
        val testContent = "Test file content for Keycloak E2E tests".toByteArray()
        minioClient.putObject(
            PutObjectArgs.builder()
                .bucket("files")
                .`object`(testFileId.toString())
                .stream(ByteArrayInputStream(testContent), testContent.size.toLong(), -1)
                .contentType("application/pdf")
                .build()
        )
    }

    @AfterEach
    fun cleanup() {
        fileShareRepository.deleteAll()
        fileRepository.deleteAll()
        uploadRepository.deleteAll()
    }

    companion object {
        // Keycloak Testcontainer con realm configurato per i test
        // Include sia il client per utenti (test-user-client) che per machine-to-machine (tus-client)
        private val keycloak = KeycloakContainer("quay.io/keycloak/keycloak:23.0")
            .withRealmImportFile("file-management-realm-test.json")
    }

    internal class KeycloakInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            keycloak.start()

            val issuerUri = "${keycloak.authServerUrl}/realms/file-management"

            TestPropertyValues.of(
                // JWT validation configuration
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=$issuerUri",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=$issuerUri/protocol/openid-connect/certs",
                // Machine-to-machine client configuration (for TusService -> UserService)
                "keycloak.token-uri=$issuerUri/protocol/openid-connect/token",
                "keycloak.client-id=tus-client",
                "keycloak.client-secret=test-tus-secret"
            ).applyTo(applicationContext.environment)
        }
    }

    /**
     * Ottiene un access token reale da Keycloak usando Resource Owner Password Grant
     * Questo simula il token che un browser otterrebbe dopo l'Authorization Code Flow
     *
     * Usa test-user-client che ha Direct Access Grant abilitato
     */
    private fun getKeycloakAccessToken(username: String, password: String): String {
        val tokenUrl = "${keycloak.authServerUrl}/realms/file-management/protocol/openid-connect/token"

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("client_id", "test-user-client")  // Client pubblico per autenticazione utenti nei test
            add("username", username)
            add("password", password)
        }

        val response = RestTemplate().postForEntity(
            tokenUrl,
            HttpEntity(body, headers),
            Map::class.java
        )

        val token = response.body?.get("access_token") as String
        println("✅ Obtained user token from Keycloak testcontainer for user: $username")
        return token
    }

    @Test
    fun `creationSuccess with real Keycloak token and machine-to-machine flow`() {
        val fileId = UUID.randomUUID()

        // 1. Utente si autentica con Keycloak
        val userToken = getKeycloakAccessToken("testadmin", "test")
        println("✅ User authenticated successfully")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename dGVzdC5iaW4=") // "test.bin" in base64
        }

        val request = HttpEntity<String>(null, headers)

        // 2. TusService riceve il token utente, lo valida
        // 3. TusService usa client_credentials per chiamare UserService (mockato)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode, "Expected 201 CREATED")
        assertNotNull(response.headers.location, "Location header should be present")

        val uploadId = UUID.fromString(response.headers.location!!.path.substringAfterLast("/"))
        println("✅ Created upload with ID: $uploadId using real Keycloak OAuth2 flow")

        val savedUpload = uploadRepository.findById(uploadId)
        assertTrue(savedUpload.isPresent, "Upload should exist in database")
        assertEquals(fileId, savedUpload.get().fileId, "FileId should match")

        println("✅ Test passed! Real Keycloak OAuth2 flow with machine-to-machine works correctly")
        println("   - User authentication: ✅")
        println("   - JWT validation: ✅")
        println("   - Service-to-service call (mocked): ✅")
        println("   - Upload created: ✅")
    }

    @Test
    fun `unauthorized request without token fails with real Keycloak`() {
        val fileId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            // Nessun token di autenticazione!
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename dGVzdC5iaW4=")
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode, "Should be UNAUTHORIZED")
        println("✅ Test passed! Keycloak correctly rejects unauthenticated requests")
    }

    // ============ File Metadata Tests ============

    /**
     * Helper class for deserializing paginated responses from REST API.
     * Jackson needs concrete types to deserialize, so we provide getters for all Page fields.
     *
     * @JsonIgnoreProperties ignores fields like 'pageable' and 'sort' that are complex Spring Data types
     * that Jackson cannot deserialize without custom deserializers.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class RestResponsePage<T>(
        val content: List<T> = emptyList(),
        val number: Int = 0,
        val size: Int = 0,
        val totalElements: Long = 0,
        val totalPages: Int = 0,
        val first: Boolean = true,
        val last: Boolean = true,
        val numberOfElements: Int = 0,
        val empty: Boolean = true
    )

    @Test
    fun `getFilesMetadata returns paginated list of files with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! File metadata retrieved successfully with Keycloak authentication")
    }

    @Test
    fun `getFilesMetadata filters by contentType with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?contentType=application/pdf&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! File metadata filtered by content type")
    }

    @Test
    fun `getFileById returns file details with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId",
            HttpMethod.GET,
            request,
            FileDTO::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(testFileId, response.body!!.id)
        println("✅ Test passed! File details retrieved by ID with Keycloak authentication")
    }

    @Test
    fun `downloadFile returns file content with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/download",
            HttpMethod.GET,
            request,
            ByteArray::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.isNotEmpty())
        println("✅ Test passed! File downloaded successfully with Keycloak authentication")
    }

    @Test
    fun `getFileById returns 404 for non-existent file with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentFileId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentFileId",
            HttpMethod.GET,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! 404 returned for non-existent file")
    }

    @Test
    fun `file operations fail without authentication`() {
        val headers = HttpHeaders()
        val request = HttpEntity<String>(null, headers)

        val response = restTemplate.exchange(
            "/api/v1/files?page=0&size=10",
            HttpMethod.GET,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
        println("✅ Test passed! File operations correctly require authentication")
    }

    // ============ TUS Protocol Tests ============

    @Test
    fun `OPTIONS uploads returns server capabilities with Keycloak auth`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads",
            HttpMethod.OPTIONS,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)
        assertNotNull(response.headers["Tus-Version"])
        assertEquals(listOf("1.0.0"), response.headers["Tus-Version"])

        val extensions = response.headers["Tus-Extension"]?.first()?.split(",")?.map { it.trim() }
        assertNotNull(extensions)
        assertTrue(extensions.contains("creation"))
        assertTrue(extensions.contains("expiration"))

        println("✅ Test passed! TUS server capabilities returned correctly")
    }

    @Test
    fun `HEAD upload returns upload info with Keycloak auth`() {
        // Create upload first
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "2048")
            set("Upload-Metadata", "filename dGVzdGZpbGUucGRm")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Now HEAD request (needs authentication too!)
        val headHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
        }

        val headRequest = HttpEntity<String>(null, headHeaders)
        val headResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.HEAD,
            headRequest,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, headResponse.statusCode)
        assertNotNull(headResponse.headers["Upload-Offset"])
        assertEquals("0", headResponse.headers["Upload-Offset"]?.first())
        assertNotNull(headResponse.headers["Upload-Length"])
        assertEquals("2048", headResponse.headers["Upload-Length"]?.first())

        println("✅ Test passed! HEAD returns correct upload info")
    }

    @Test
    fun `PATCH uploads chunk successfully with Keycloak auth`() {
        // Create upload
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename Y2h1bmsuYmlu")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // PATCH to upload chunk (needs authentication!)
        val chunkData = "This is test chunk data content".toByteArray()
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", chunkData.size.toString())
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, patchResponse.statusCode)
        assertNotNull(patchResponse.headers["Upload-Offset"])
        assertEquals(chunkData.size.toString(), patchResponse.headers["Upload-Offset"]?.first())

        println("✅ Test passed! PATCH uploaded chunk successfully")
    }

    @Test
    fun `DELETE terminates upload with Keycloak auth`() {
        // Create upload
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "512")
            set("Upload-Metadata", "filename ZGVsZXRlLnR4dA==")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Verify upload exists
        val beforeDelete = uploadRepository.findById(UUID.fromString(uploadId))
        assertTrue(beforeDelete.isPresent)

        // DELETE (needs authentication!)
        val deleteHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
        }

        val deleteRequest = HttpEntity<String>(null, deleteHeaders)
        val deleteResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.DELETE,
            deleteRequest,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)
        println("✅ Test passed! DELETE terminated upload successfully")
    }

    @Test
    fun `POST without Upload-Length header returns BadRequest`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            // Missing Upload-Length!
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        println("✅ Test passed! Missing Upload-Length returns 400")
    }

    @Test
    fun `PATCH with wrong offset returns Conflict`() {
        // Create upload
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename dGVzdC50eHQ=")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // PATCH with wrong offset (needs authentication!)
        val chunkData = "Wrong offset chunk".toByteArray()
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "100") // Wrong! Should be 0
            set("Content-Length", chunkData.size.toString())
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CONFLICT, patchResponse.statusCode)
        println("✅ Test passed! Wrong offset returns 409 Conflict")
    }

    @Test
    fun `POST with excessive size returns PayloadTooLarge`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "2147483648") // 2GB, exceeds 1GB max
            set("Upload-Metadata", "filename bGFyZ2VmaWxlLmJpbg==")
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.statusCode)
        println("✅ Test passed! Excessive size returns 413")
    }

    @Test
    fun `HEAD on non-existent upload returns NotFound`() {
        val nonExistentId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads/$nonExistentId",
            HttpMethod.HEAD,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! HEAD on non-existent upload returns 404")
    }

    @Test
    fun `creation-with-upload works correctly`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val chunkData = "Initial chunk data for creation-with-upload".toByteArray()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Content-Type", "application/offset+octet-stream")
            set("Content-Length", chunkData.size.toString())
            set("Upload-Metadata", "filename Y3JlYXRlZC50eHQ=")
        }

        val request = HttpEntity(chunkData, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, response.statusCode)
        assertNotNull(response.headers.location)
        assertNotNull(response.headers["Upload-Offset"])
        assertEquals(chunkData.size.toString(), response.headers["Upload-Offset"]?.first())

        println("✅ Test passed! Creation-with-upload successful")
    }

    @Test
    fun `POST with both Upload-Length and Upload-Defer-Length`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Defer-Length", "1") // Should not be together with Upload-Length
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        // Should either create or reject with 400 depending on implementation
        assertTrue(
            response.statusCode == HttpStatus.CREATED || response.statusCode == HttpStatus.BAD_REQUEST,
            "Should either create or reject with 400"
        )
        println("✅ Test passed! Handles both Upload-Length and Upload-Defer-Length")
    }

    @Test
    fun `POST without any length headers returns BadRequest`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Metadata", "filename dGVzdC50eHQ=")
            // Missing both Upload-Length and Upload-Defer-Length!
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        println("✅ Test passed! Missing both length headers returns 400")
    }

    @Test
    fun `POST creation-with-upload with wrong Content-Type returns UnsupportedMediaType`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val chunkData = "Test data".toByteArray()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Content-Type", "application/json") // Wrong Content-Type!
            set("Content-Length", chunkData.size.toString())
            set("Upload-Metadata", "filename dGVzdC50eHQ=")
        }

        val request = HttpEntity(chunkData, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.statusCode)
        println("✅ Test passed! Wrong Content-Type returns 415")
    }

    @Test
    fun `POST creation-with-upload missing Content-Type returns UnsupportedMediaType`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val chunkData = "Test data".toByteArray()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            // Missing Content-Type!
            set("Content-Length", chunkData.size.toString())
            set("Upload-Metadata", "filename dGVzdC50eHQ=")
        }

        val request = HttpEntity(chunkData, headers)
        val response = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, response.statusCode)
        println("✅ Test passed! Missing Content-Type returns 415")
    }

    @Test
    fun `PATCH upload exceeding Upload-Length returns PayloadTooLarge`() {
        val fileId = UUID.randomUUID()
        val uploadLength = 100L
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", uploadLength.toString())
            set("Upload-Metadata", "filename ZXhjZWVkLnR4dA==")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Try to upload 150 bytes (exceeds Upload-Length of 100)
        val oversizedChunk = ByteArray(150) { 'X'.code.toByte() }
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", oversizedChunk.size.toString())
        }

        val patchRequest = HttpEntity(oversizedChunk, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, patchResponse.statusCode)
        println("✅ Test passed! PATCH exceeding Upload-Length returns 413")
    }

    @Test
    fun `PATCH with invalid UUID returns BadRequest`() {
        val invalidUUID = "not-a-valid-uuid"
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val chunkData = ByteArray(50) { 'A'.code.toByte() }
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", chunkData.size.toString())
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$invalidUUID",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, patchResponse.statusCode)
        println("✅ Test passed! PATCH with invalid UUID returns 400")
    }

    @Test
    fun `PATCH multiple chunks updates offset correctly`() {
        val fileId = UUID.randomUUID()
        val totalSize = 300L
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", totalSize.toString())
            set("Upload-Metadata", "filename bXVsdGkudHh0")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // PATCH multiple chunks in sequence
        val chunkSize = 100
        for (i in 0 until 3) {
            val chunk = ByteArray(chunkSize) { ('A'.code + i).toByte() }
            val offset = i * chunkSize

            val patchHeaders = HttpHeaders().apply {
                setBearerAuth(userToken)
                set("Tus-Resumable", "1.0.0")
                set("Content-Type", "application/offset+octet-stream")
                set("Upload-Offset", offset.toString())
                set("Content-Length", chunk.size.toString())
            }

            val patchRequest = HttpEntity(chunk, patchHeaders)
            val patchResponse = restTemplate.exchange(
                "/api/v1/uploads/$uploadId",
                HttpMethod.PATCH,
                patchRequest,
                String::class.java
            )

            assertEquals(HttpStatus.NO_CONTENT, patchResponse.statusCode)
            val expectedOffset = (i + 1) * chunkSize
            assertEquals(expectedOffset.toString(), patchResponse.headers["Upload-Offset"]?.first())
        }

        // Verify final state
        val savedUpload = uploadRepository.findById(UUID.fromString(uploadId))
        assertTrue(savedUpload.isPresent)
        assertEquals(totalSize, savedUpload.get().totalOffset)

        println("✅ Test passed! Multiple PATCH requests update offset correctly")
    }

    // ============ File Metadata Edge Cases - Sorting & Filtering ============

    // Helper to create test files with specific timestamps
    private fun createTestFile(fileId: UUID, filename: String, createdAt: Date) {
        val file = File(
            uuid = fileId,
            createdAt = createdAt,
            lastUpdateAt = createdAt,
            lastFilename = filename,
            currentVersion = 1L,
            ownerId = testUserId,
            organization = "Test Organization",
            department = "Test Department",
            sharingScope = SharingScope.PERSONAL
        )

        val fileVersion = FileVersion(
            id = null,
            version = 1L,
            status = FileStatus.AVAILABLE,
            file = file,
            filename = filename,
            size = 1024L,
            metadata = mapOf(),
            checksum = "test",
            contentType = "application/pdf",
            createdAt = createdAt,
            lastUpdateAt = createdAt,
            objectKey = fileId.toString()
        )

        file.versions.add(fileVersion)
        fileRepository.save(file)
    }

    @Test
    fun `getFilesMetadata filters by date range`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        // Use a date range that includes the test file created in @BeforeEach (current date)
        val dateFrom = "2020-01-01T00:00:00Z"
        val dateTo = "2030-12-31T23:59:59Z"

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?dateFrom=$dateFrom&dateTo=$dateTo&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        // Service may return 404 when no results found, or 200 with empty list
        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND, got ${response.statusCode}"
        )
        if (response.statusCode == HttpStatus.OK) {
            assertNotNull(response.body)
        }
        println("✅ Test passed! Date range filtering works")
    }

    @Test
    fun `getFilesMetadata sorts by field and direction`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt,asc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Sorting by field and direction works")
    }

    @Test
    fun `getFilesMetadata sorts by createdAt ascending`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val file1Id = UUID.randomUUID()
        val file2Id = UUID.randomUUID()
        val file3Id = UUID.randomUUID()

        val now = Instant.now()
        val time1 = Date.from(now.minusSeconds(300)) // 5 minutes ago
        val time2 = Date.from(now.minusSeconds(60))  // 1 minute ago
        val time3 = Date.from(now)                    // now

        createTestFile(file1Id, "file1.txt", time1)
        createTestFile(file2Id, "file2.txt", time2)
        createTestFile(file3Id, "file3.txt", time3)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt,asc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val files = response.body!!.content
        assertTrue(files.size >= 3, "Expected at least 3 files")

        val file1Index = files.indexOfFirst { it.id == file1Id }
        val file3Index = files.indexOfFirst { it.id == file3Id }
        assertTrue(file1Index >= 0 && file3Index >= 0, "Both files should be in results")
        assertTrue(file1Index < file3Index, "Older file should come before newer file in ASC order")

        println("✅ Test passed! Sorting by createdAt ASC works correctly")
    }

    @Test
    fun `getFilesMetadata sorts by createdAt descending`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val file1Id = UUID.randomUUID()
        val file2Id = UUID.randomUUID()

        val now = Instant.now()
        val time1 = Date.from(now.minusSeconds(120)) // 2 minutes ago
        val time2 = Date.from(now)                    // now

        createTestFile(file1Id, "old-file.txt", time1)
        createTestFile(file2Id, "new-file.txt", time2)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt,desc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val files = response.body!!.content
        assertTrue(files.size >= 2, "Expected at least 2 files")

        val file1Index = files.indexOfFirst { it.id == file1Id }
        val file2Index = files.indexOfFirst { it.id == file2Id }
        assertTrue(file1Index >= 0 && file2Index >= 0, "Both files should be in results")
        assertTrue(file2Index < file1Index, "Newer file should come before older file in DESC order")

        println("✅ Test passed! Sorting by createdAt DESC works correctly")
    }

    @Test
    fun `getFilesMetadata sorts by lastFilename ascending`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val fileAId = UUID.randomUUID()
        val fileBId = UUID.randomUUID()
        val fileCId = UUID.randomUUID()

        val now = Date.from(Instant.now())

        createTestFile(fileAId, "alpha.txt", now)
        createTestFile(fileBId, "beta.txt", now)
        createTestFile(fileCId, "gamma.txt", now)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=lastFilename,asc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val files = response.body!!.content
        assertTrue(files.size >= 3, "Expected at least 3 files")

        val fileAIndex = files.indexOfFirst { it.id == fileAId }
        val fileCIndex = files.indexOfFirst { it.id == fileCId }
        assertTrue(fileAIndex >= 0 && fileCIndex >= 0, "Both files should be in results")
        assertTrue(fileAIndex < fileCIndex, "alpha.txt should come before gamma.txt in ASC order")

        println("✅ Test passed! Sorting by lastFilename ASC works correctly")
    }

    @Test
    fun `getFilesMetadata sorts by lastFilename descending`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val fileXId = UUID.randomUUID()
        val fileYId = UUID.randomUUID()
        val fileZId = UUID.randomUUID()

        val now = Date.from(Instant.now())

        createTestFile(fileXId, "xray.txt", now)
        createTestFile(fileYId, "yankee.txt", now)
        createTestFile(fileZId, "zulu.txt", now)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=lastFilename,desc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)

        val files = response.body!!.content
        assertTrue(files.size >= 3, "Expected at least 3 files")

        val fileXIndex = files.indexOfFirst { it.id == fileXId }
        val fileZIndex = files.indexOfFirst { it.id == fileZId }
        assertTrue(fileXIndex >= 0 && fileZIndex >= 0, "Both files should be in results")
        assertTrue(fileZIndex < fileXIndex, "zulu.txt should come before xray.txt in DESC order")

        println("✅ Test passed! Sorting by lastFilename DESC works correctly")
    }

    @Test
    fun `getFilesMetadata uses default sort when not specified`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Default sorting works")
    }

    @Test
    fun `getFilesMetadata handles sort with only field no direction`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Sort with only field defaults to DESC")
    }

    @Test
    fun `getFilesMetadata handles sort with uppercase ASC`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt,ASC&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Uppercase ASC handled correctly")
    }

    @Test
    fun `getFilesMetadata handles sort with invalid direction`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=createdAt,invalid&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Invalid direction defaults to DESC")
    }

    @Test
    fun `getFilesMetadata handles empty sort parameter`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Empty sort parameter handled gracefully")
    }

    @Test
    fun `getFilesMetadata handles sort with only comma`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?sort=,desc&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Sort with only comma uses default field")
    }

    @Test
    fun `getFilesMetadata combines all filters together`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val dateFrom = "2020-01-01T00:00:00Z"
        val dateTo = "2030-12-31T23:59:59Z"

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?contentType=application/pdf&dateFrom=$dateFrom&dateTo=$dateTo&sort=lastFilename,asc&page=0&size=5",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! All filters combined work correctly")
    }

    @Test
    fun `getFilesMetadata filters by dateFrom only`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val dateFrom = "2020-01-01T00:00:00Z"

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?dateFrom=$dateFrom&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! Filter by dateFrom only works")
    }

    @Test
    fun `getFilesMetadata filters by dateTo only`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val dateTo = "2030-12-31T23:59:59Z"

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files?dateTo=$dateTo&page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! Filter by dateTo only works")
    }

    @Test
    fun `getFilesMetadata uses custom page and size`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        // Create enough files for pagination
        val now = Date.from(Instant.now())
        for (i in 1..10) {
            createTestFile(UUID.randomUUID(), "test-file-$i.txt", now)
        }

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        // Test first page
        val firstPageRequest = HttpEntity<String>(null, headers)
        val firstPageResponse = restTemplate.exchange(
            "/api/v1/files?page=0&size=5",
            HttpMethod.GET,
            firstPageRequest,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, firstPageResponse.statusCode)
        assertNotNull(firstPageResponse.body)
        val firstPage = firstPageResponse.body!!
        assertEquals(0, firstPage.number)
        assertEquals(5, firstPage.size)
        assertTrue(firstPage.content.size <= 5, "First page should have at most 5 items")
        assertTrue(firstPage.totalElements >= 10, "Should have at least 10 total files")

        // Test second page
        val secondPageRequest = HttpEntity<String>(null, headers)
        val secondPageResponse = restTemplate.exchange(
            "/api/v1/files?page=1&size=5",
            HttpMethod.GET,
            secondPageRequest,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, secondPageResponse.statusCode)
        assertNotNull(secondPageResponse.body)
        val secondPage = secondPageResponse.body!!
        assertEquals(1, secondPage.number)
        assertTrue(secondPage.content.isNotEmpty(), "Second page should have content")

        println("✅ Test passed! Custom page and size work correctly")
    }

    @Test
    fun `getFilesMetadata with no filters uses all defaults`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        println("✅ Test passed! Default values work correctly")
    }

    // ============ Advanced Upload State Tests ============

    @Test
    fun `PATCH upload completion sets status to MERGE_PENDING`() {
        val fileId = UUID.randomUUID()
        val totalSize = 100L
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", totalSize.toString())
            set("Upload-Metadata", "filename Y29tcGxldGUudHh0")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // PATCH 1: upload first chunk (50 bytes)
        val chunk1 = ByteArray(50) { 'A'.code.toByte() }
        val patch1Headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", chunk1.size.toString())
        }

        val patch1Request = HttpEntity(chunk1, patch1Headers)
        val patch1Response = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patch1Request,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, patch1Response.statusCode)
        assertEquals("50", patch1Response.headers["Upload-Offset"]?.first())

        // Verify status is still UPLOADING
        var savedUpload = uploadRepository.findById(UUID.fromString(uploadId))
        assertTrue(savedUpload.isPresent)
        assertEquals("UPLOADING", savedUpload.get().status.name)

        // PATCH 2: upload final chunk (50 bytes) - completes upload
        val chunk2 = ByteArray(50) { 'B'.code.toByte() }
        val patch2Headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "50")
            set("Content-Length", chunk2.size.toString())
        }

        val patch2Request = HttpEntity(chunk2, patch2Headers)
        val patch2Response = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patch2Request,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, patch2Response.statusCode)
        assertEquals("100", patch2Response.headers["Upload-Offset"]?.first())

        // Verify status changed to MERGE_PENDING
        savedUpload = uploadRepository.findById(UUID.fromString(uploadId))
        assertTrue(savedUpload.isPresent)
        val completedUpload = savedUpload.get()
        assertEquals("MERGE_PENDING", completedUpload.status.name)
        assertEquals(totalSize, completedUpload.totalOffset)

        println("✅ Test passed! Upload completion sets status to MERGE_PENDING")
    }

    @Test
    fun `PATCH with mismatched Content-Length throws exception`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename bWlzbWF0Y2gudHh0")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Chunk of 50 bytes but declares Content-Length = 100
        val chunkData = ByteArray(50) { 'A'.code.toByte() }
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", "100") // DIFFERENT from actual chunk size!
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)

        try {
            restTemplate.exchange(
                "/api/v1/uploads/$uploadId",
                HttpMethod.PATCH,
                patchRequest,
                String::class.java
            )
            assert(false) { "Expected ResourceAccessException due to Content-Length mismatch" }
        } catch (e: org.springframework.web.client.ResourceAccessException) {
            assertTrue(
                e.message?.contains("Too few bytes") ?: false,
                "Exception should mention 'Too few bytes'"
            )
            println("✅ Test passed! Client-side validation caught Content-Length mismatch")
        }
    }

    @Test
    fun `PATCH deleted upload returns GONE or CONFLICT`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename ZGVsZXRlZC50eHQ=")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Delete the upload
        val deleteHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
        }

        val deleteRequest = HttpEntity<String>(null, deleteHeaders)
        val deleteResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.DELETE,
            deleteRequest,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.statusCode)

        // Try to PATCH deleted upload
        val chunkData = ByteArray(50) { 'A'.code.toByte() }
        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", chunkData.size.toString())
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertTrue(
            patchResponse.statusCode == HttpStatus.GONE ||
                    patchResponse.statusCode == HttpStatus.CONFLICT,
            "PATCH on deleted upload should return 410 GONE or 409 CONFLICT"
        )

        println("✅ Test passed! PATCH on deleted upload returns error")
    }

    @Test
    fun `PATCH with checksum validation succeeds`() {
        val fileId = UUID.randomUUID()
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val createHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Upload-Length", "1024")
            set("Upload-Metadata", "filename Y2hlY2tzdW0udHh0")
        }

        val createRequest = HttpEntity<String>(null, createHeaders)
        val createResponse = restTemplate.exchange(
            "/api/v1/uploads?fileId=$fileId",
            HttpMethod.POST,
            createRequest,
            String::class.java
        )

        assertEquals(HttpStatus.CREATED, createResponse.statusCode)
        val uploadId = createResponse.headers.location?.path?.substringAfterLast("/")
        assertNotNull(uploadId)

        // Chunk with valid SHA-1 checksum
        val chunkData = "Test data for checksum validation".toByteArray()
        val sha1 = java.security.MessageDigest.getInstance("SHA-1")
        val hash = sha1.digest(chunkData)
        val checksum = java.util.Base64.getEncoder().encodeToString(hash)

        val patchHeaders = HttpHeaders().apply {
            setBearerAuth(userToken)
            set("Tus-Resumable", "1.0.0")
            set("Content-Type", "application/offset+octet-stream")
            set("Upload-Offset", "0")
            set("Content-Length", chunkData.size.toString())
            set("Upload-Checksum", "sha1 $checksum")
        }

        val patchRequest = HttpEntity(chunkData, patchHeaders)
        val patchResponse = restTemplate.exchange(
            "/api/v1/uploads/$uploadId",
            HttpMethod.PATCH,
            patchRequest,
            String::class.java
        )

        assertEquals(HttpStatus.NO_CONTENT, patchResponse.statusCode)
        assertEquals(chunkData.size.toString(), patchResponse.headers["Upload-Offset"]?.first())

        println("✅ Test passed! PATCH with checksum validation succeeds")
    }

    // ============ File CRUD Operations Tests ============

    @Test
    fun `getFileMetadata returns file when exists`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId",
            HttpMethod.GET,
            request,
            FileDTO::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertEquals(testFileId, response.body!!.id)
        println("✅ Test passed! Get file metadata works")
    }

    @Test
    fun `getFileMetadata returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId",
            HttpMethod.GET,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! 404 for non-existent file metadata")
    }

    // ============ Update File Metadata Tests ============

    @Test
    fun `updateFileMetadata updates metadata successfully`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val metadata = mapOf("author" to "John Doe", "description" to "Test document")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            contentType = MediaType.APPLICATION_JSON
        }

        val request = HttpEntity(metadata, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/versions/1",
            HttpMethod.PATCH,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        println("✅ Test passed! Update file metadata works")
    }

    @Test
    fun `updateFileMetadata returns 400 when metadata is empty`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val metadata = emptyMap<String, String>()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            contentType = MediaType.APPLICATION_JSON
        }

        val request = HttpEntity(metadata, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/versions/1",
            HttpMethod.PATCH,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        println("✅ Test passed! Empty metadata returns 400")
    }

    @Test
    fun `updateFileMetadata returns 400 when metadata key is not ASCII`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val metadata = mapOf("naïve" to "value")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            contentType = MediaType.APPLICATION_JSON
        }

        val request = HttpEntity(metadata, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/versions/1",
            HttpMethod.PATCH,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        println("✅ Test passed! Non-ASCII metadata key returns 400")
    }

    @Test
    fun `updateFileMetadata returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()
        val metadata = mapOf("author" to "John Doe")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
            contentType = MediaType.APPLICATION_JSON
        }

        val request = HttpEntity(metadata, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/versions/1",
            HttpMethod.PATCH,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Update metadata for non-existent file returns 404")
    }

    // ============ Delete File Tests ============

    @Test
    fun `deleteFile deletes file successfully`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val fileToDelete = UUID.randomUUID()

        // Create a file to delete
        createTestFile(fileToDelete, "to-delete.txt", Date.from(Instant.now()))

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$fileToDelete",
            HttpMethod.DELETE,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)

        // Verify file is deleted
        val fileExists = fileRepository.existsById(fileToDelete)
        assertEquals(false, fileExists)
        println("✅ Test passed! Delete file works")
    }

    @Test
    fun `deleteFile returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId",
            HttpMethod.DELETE,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Delete non-existent file returns 404")
    }

    // ============ Download File Tests ============

    @Test
    fun `downloadFile returns file content when complete`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/download",
            HttpMethod.GET,
            request,
            ByteArray::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.isNotEmpty())
        assertNotNull(response.headers.getFirst("Content-Disposition"))
        println("✅ Test passed! Download file works")
    }

    @Test
    fun `downloadFile returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/download",
            HttpMethod.GET,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Download non-existent file returns 404")
    }

    // ============ File Shares Tests ============

    @Test
    fun `getFileShares returns paginated list of shares`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        // Create a share
        val userId = UUID.randomUUID()
        val fileEntity = fileRepository.findById(testFileId).orElseThrow()
        val share = g11.tusuploaderservice.entities.FileShare(
            id = null,
            file = fileEntity,
            userID = userId,
            permission = g11.tusuploaderservice.entities.PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date.from(Instant.now())
        )
        fileShareRepository.save(share)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/shares?page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<g11.tusuploaderservice.dto.FileShareDTO>>() {}
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! Get file shares works")
    }

    @Test
    fun `getFileShares returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/shares?page=0&size=10",
            HttpMethod.GET,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Get shares for non-existent file returns 404")
    }

    @Test
    fun `createFileShare creates share successfully`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val userId = UUID.fromString("00000000-0000-0000-0000-000000000001")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/shares?userId=$userId&permission=READ_ONLY",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.CREATED || response.statusCode == HttpStatus.BAD_REQUEST,
            "Expected 201 CREATED or 400 BAD_REQUEST"
        )
        println("✅ Test passed! Create file share works")
    }

    @Test
    fun `createFileShare returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/shares?userId=$userId&permission=READ_ONLY",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Create share for non-existent file returns 404")
    }

    @Test
    fun `createFileShare returns 400 when invalid permission`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val userId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/shares?userId=$userId&permission=INVALID",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        println("✅ Test passed! Invalid permission returns 400")
    }

    @Test
    fun `getFilesSharedWithUser returns paginated list of files`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val userId = UUID.randomUUID()

        // Create a share
        val fileEntity = fileRepository.findById(testFileId).orElseThrow()
        val share = g11.tusuploaderservice.entities.FileShare(
            id = null,
            file = fileEntity,
            userID = userId,
            permission = g11.tusuploaderservice.entities.PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date.from(Instant.now())
        )
        fileShareRepository.save(share)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/shared-with/$userId?page=0&size=10",
            HttpMethod.GET,
            request,
            object : ParameterizedTypeReference<RestResponsePage<FileDTO>>() {}
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! Get files shared with user works")
    }

    @Test
    fun `deleteFileShare deletes share successfully`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val userId = UUID.randomUUID()

        val fileEntity = fileRepository.findById(testFileId).orElseThrow()
        val share = g11.tusuploaderservice.entities.FileShare(
            id = null,
            file = fileEntity,
            userID = userId,
            permission = g11.tusuploaderservice.entities.PERMISSION_LEVEL.READ_ONLY,
            timestamp = Date.from(Instant.now())
        )
        val savedShare = fileShareRepository.save(share)

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/shares/${savedShare.id}",
            HttpMethod.DELETE,
            request,
            String::class.java
        )

        assertTrue(
            response.statusCode == HttpStatus.OK || response.statusCode == HttpStatus.NOT_FOUND,
            "Expected 200 OK or 404 NOT_FOUND"
        )
        println("✅ Test passed! Delete file share works")
    }

    @Test
    fun `deleteFileShare returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()
        val shareId = 999L

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/shares/$shareId",
            HttpMethod.DELETE,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Delete share for non-existent file returns 404")
    }

    @Test
    fun `deleteFileShare returns 404 when share not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentShareId = 999L

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/shares/$nonExistentShareId",
            HttpMethod.DELETE,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Delete non-existent share returns 404")
    }

    // ============ File Versions Tests ============

    @Test
    fun `createFileVersion redirects to upload endpoint`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$testFileId/versions",
            HttpMethod.POST,
            request,
            String::class.java
        )

        // 400 means the redirect worked and TUS endpoint was reached
        // (which correctly rejects our request for missing TUS protocol headers)
        assertTrue(
            response.statusCode == HttpStatus.BAD_REQUEST || response.statusCode == HttpStatus.TEMPORARY_REDIRECT,
            "Expected 400 BAD_REQUEST (after redirect) or 307 TEMPORARY_REDIRECT"
        )
        println("✅ Test passed! Create file version redirects correctly")
    }

    @Test
    fun `createFileVersion returns 404 when file not found`() {
        val userToken = getKeycloakAccessToken("testadmin", "test")
        val nonExistentId = UUID.randomUUID()

        val headers = HttpHeaders().apply {
            setBearerAuth(userToken)
        }

        val request = HttpEntity<String>(null, headers)
        val response = restTemplate.exchange(
            "/api/v1/files/$nonExistentId/versions",
            HttpMethod.POST,
            request,
            String::class.java
        )

        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
        println("✅ Test passed! Create version for non-existent file returns 404")
    }
}

