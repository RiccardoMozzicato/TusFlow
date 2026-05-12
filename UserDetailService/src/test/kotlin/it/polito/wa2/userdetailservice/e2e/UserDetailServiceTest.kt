package it.polito.wa2.userdetailservice.e2e

import it.polito.wa2.userdetailservice.IntegrationTest
import it.polito.wa2.userdetailservice.config.KeycloakMockConfig
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.enums.UserRole
import io.mockk.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertNotNull
import org.keycloak.admin.client.Keycloak
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.client.getForEntity
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Import(KeycloakMockConfig::class)
class UserDetailServiceTest : IntegrationTest() {
    @Autowired private lateinit var restTemplate: TestRestTemplate
    @Autowired private lateinit var dataSource: DataSource
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var keycloakAdminClient: Keycloak

    @BeforeEach
    fun setupDB() {
        // Ensure a clean state before each test to avoid inter-test interference
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE")
        val populator = ResourceDatabasePopulator(ClassPathResource("test-data.sql"))
        DatabasePopulatorUtils.execute(populator, dataSource)
    }

    private fun getKeycloakAccessToken(userRole: UserRole): String {
        val keycloakBaseUrl = IntegrationTest.keycloakContainer.authServerUrl.trimEnd('/')
        val tokenUrl = "$keycloakBaseUrl/realms/${IntegrationTest.realmName}/protocol/openid-connect/token"

        val roleName = userRole.name.removePrefix("ROLE_")
        val realmUser = IntegrationTest.usersByRole[roleName]
            ?: error("No user found in realm for role '$roleName'")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }

        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "password")
            add("client_id", IntegrationTest.gatewayClientId)
            add("client_secret", IntegrationTest.gatewayClientSecret)
            add("username", realmUser.username)
            add("password", realmUser.password)
        }

        val response = RestTemplate().postForEntity(
            tokenUrl,
            HttpEntity(body, headers),
            Map::class.java
        )

        val token = response.body?.get("access_token") as String
        println("Obtained Keycloak access token: $token")
        return token
    }



    @Test
    fun `getUser() - 200 ok `() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<UserDTO> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.GET,
            entity,
            UserDTO::class.java
        )

        // assert
        assertEquals(HttpStatus.OK, response.statusCode)
        val user = response.body
        assertNotNull(user)
        assertEquals(userId, user.id)
    }

    @Test
    fun `getUser() - 400 invalid uuid `() {
        // arrange
        val invalidUserId = "invalid-uuid"
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$invalidUserId",
            HttpMethod.GET,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `getUser() - 401 unauthorized `() {
        // arrange
        val userId = UUID.randomUUID()

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.getForEntity("/api/v1/users/$userId")

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `getUser() - 404 user not found `() {
        // arrange
        val userId = UUID.randomUUID()
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.GET,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // GET USERS

    @Test
    fun `getUsers() - 200 ok with default pagination`() {
        // arrange
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<String> = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.GET,
            entity,
            String::class.java
        )

        // assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        // Verify that response contains pagination metadata
        assert(response.body!!.contains("\"content\""))
        assert(response.body!!.contains("\"totalElements\""))
        assert(response.body!!.contains("\"totalPages\""))
    }

    @Test
    fun `getUsers() - 200 ok with custom pagination`() {
        // arrange
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<String> = restTemplate.exchange(
            "/api/v1/users?page=0&size=5",
            HttpMethod.GET,
            entity,
            String::class.java
        )

        // assert
        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assert(response.body!!.contains("\"content\""))
        assert(response.body!!.contains("\"size\":5"))
    }

    @Test
    fun `getUsers() - 401 unauthorized`() {
        // arrange
        val headers = HttpHeaders()
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.GET,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    // CREATE USER

    @Test
    fun `createUser() - 401 unauthorized`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "franco.bollo",
            email = "test@example.com",
            organization = "Test Org",
            role = UserRole.ROLE_staff,
            department = null,
            storageQuota = 123456
        )
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userCreationDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `createUser() - 500`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            email = "s347492@studenti.polito.it",
            organization = "Test Org",
            role = UserRole.ROLE_staff,
            department = null,
            storageQuota = 123456,
            username = "riccardo"
        )

        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userCreationDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users",
            HttpMethod.POST,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
    }

    // DELETE USER

    @Test
    fun `deleteUser() - 204 no content`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<Void> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.DELETE,
            entity,
            Void::class.java
        )

        // assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify user was deleted from DB
        val userExists = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE id = ?",
            Int::class.java,
            userId
        )
        assertEquals(0, userExists)

        // Verify Keycloak was called
        verify(atLeast = 1) { keycloakAdminClient.realm("file-management") }
    }

    @Test
    fun `deleteUser() - 400 invalid uuid`() {
        // arrange
        val invalidUserId = "invalid-uuid"
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$invalidUserId",
            HttpMethod.DELETE,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `deleteUser() - 401 unauthorized`() {
        // arrange
        val userId = UUID.randomUUID()
        val headers = HttpHeaders()
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.DELETE,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `deleteUser() - 404 user not found`() {
        // arrange
        val userId = UUID.randomUUID()
        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        val entity = HttpEntity<Void>(headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.DELETE,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    // UPDATE USER

    @Test
    fun `updateUser() - 204 no content`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val userDTO = UserDTO(
            id = userId,
            fullName = "Riccardo Updated",
            email = "riccardo.updated@example.com",
            organization = "Updated Organization",
            department = "Updated Department",
            role = UserRole.ROLE_manager,
            storageQuota = 2000000L
        )

        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userDTO, headers)

        // act
        val response: ResponseEntity<Void> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.PUT,
            entity,
            Void::class.java
        )

        // assert
        assertEquals(HttpStatus.NO_CONTENT, response.statusCode)

        // Verify user was updated in DB
        val updatedUser = jdbcTemplate.queryForMap(
            "SELECT * FROM users WHERE id = ?",
            userId
        )
        assertEquals(userDTO.fullName, updatedUser["full_name"])
        assertEquals(userDTO.email, updatedUser["email"])
        assertEquals(userDTO.organization, updatedUser["organization"])
        assertEquals(userDTO.department, updatedUser["department"])
        assertEquals(1, updatedUser["role"])
        assertEquals(userDTO.storageQuota, updatedUser["storage_quota"])
    }

    @Test
    fun `updateUser() - 400 invalid uuid`() {
        // arrange
        val invalidUserId = "invalid-uuid"
        val userDTO = UserDTO(
            id = UUID.randomUUID(),
            fullName = "Test User",
            email = "test@example.com",
            organization = "Test Org",
            department = null,
            role = UserRole.ROLE_staff,
            storageQuota = 1000000L
        )

        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$invalidUserId",
            HttpMethod.PUT,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `updateUser() - 401 unauthorized`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val userDTO = UserDTO(
            id = userId,
            fullName = "Test User",
            email = "test@example.com",
            organization = "Test Org",
            department = null,
            role = UserRole.ROLE_staff,
            storageQuota = 1000000L
        )

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.PUT,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }

    @Test
    fun `updateUser() - 404 user not found`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Test User",
            email = "test@example.com",
            organization = "Test Org",
            department = null,
            role = UserRole.ROLE_staff,
            storageQuota = 1000000L
        )

        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$userId",
            HttpMethod.PUT,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
    }

    @Test
    fun `updateUser() - 409 id mismatch`() {
        // arrange
        val pathUserId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val bodyUserId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = bodyUserId,
            fullName = "Test User",
            email = "test@example.com",
            organization = "Test Org",
            department = null,
            role = UserRole.ROLE_staff,
            storageQuota = 1000000L
        )

        val token = getKeycloakAccessToken(UserRole.ROLE_admin)
        val headers = HttpHeaders()
        headers.setBearerAuth(token)
        headers.contentType = MediaType.APPLICATION_JSON
        val entity = HttpEntity(userDTO, headers)

        // act
        val response: ResponseEntity<ProblemDetail> = restTemplate.exchange(
            "/api/v1/users/$pathUserId",
            HttpMethod.PUT,
            entity,
            ProblemDetail::class.java
        )

        // assert
        assertEquals(HttpStatus.CONFLICT, response.statusCode)
    }
}
