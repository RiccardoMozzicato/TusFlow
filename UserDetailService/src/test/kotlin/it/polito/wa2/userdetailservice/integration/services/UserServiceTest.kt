package it.polito.wa2.userdetailservice.integration.services

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import it.polito.wa2.userdetailservice.IntegrationTest
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.StorageQuotaBelowUsedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.services.UserService
import jakarta.ws.rs.core.Response
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.admin.client.resource.RoleMappingResource
import org.keycloak.admin.client.resource.RoleResource
import org.keycloak.admin.client.resource.RoleScopeResource
import org.keycloak.admin.client.resource.RolesResource
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.admin.client.resource.UsersResource
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.security.test.context.support.WithMockUser
import java.net.URI
import java.time.Instant
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserServiceTest: IntegrationTest() {
    @MockkBean
    private lateinit var keycloak: Keycloak
    private var realm: String = "file-management"
    @Autowired
    private lateinit var userService: UserService
    @Autowired
    private lateinit var dataSource: DataSource
    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setupDB() {
        // Ensure a clean state before each test to avoid inter-test interference
        jdbcTemplate.execute("TRUNCATE TABLE users CASCADE")
        val populator = ResourceDatabasePopulator(ClassPathResource("test-data.sql"))
        DatabasePopulatorUtils.execute(populator, dataSource)
    }

    // updateUser() syncs name/email back to Keycloak; stub that chain so the
    // success-path tests don't fall into the "Error updating user" catch block.
    private fun mockKeycloakUserUpdate(userId: UUID) {
        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        every { userResource.toRepresentation() } returns UserRepresentation()
        justRun { userResource.update(any()) }
    }

    private fun setupJwtAuthentication(userId: String, roles: List<String>) {
        val jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject(userId)
            .claim("realm_access", mapOf("roles" to roles))
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(3600))
            .build()

        val authorities = roles.map { SimpleGrantedAuthority("ROLE_$it") }
        val authentication = JwtAuthenticationToken(jwt, authorities)
        SecurityContextHolder.getContext().authentication = authentication
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `getUsers() - successful for staff`() {
        // arrange
        setupJwtAuthentication("c3d4e5f6-a7b8-9012-cdef-123456789012", listOf("staff"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10)

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertNotNull(result)
        Assertions.assertTrue(result.content.isNotEmpty())
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `getUsers() - successful for manager`() {
        // arrange
        setupJwtAuthentication("b2c3d4e5-f6a7-8901-bcde-f12345678901", listOf("manager"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10)

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertNotNull(result)
        Assertions.assertTrue(result.content.isNotEmpty())
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - successful (page=0, pageSize=2)`() {
        // arrange
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 2)

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertEquals(2, result.content.size)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - successful (page=1, pageSize=2)`() {
        // arrange
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(pageNumber = 1, pageSize = 2)

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertEquals(1, result.content.size)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - search matches name case-insensitively within the org`() {
        // arrange: admin is in "Polytechnic University of Turin" (Mario, Riccardo, Samuele)
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10, search = "ROSSI")

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertEquals(1, result.totalElements)
        Assertions.assertEquals("Mario Rossi", result.content[0].fullName)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - search matches email substring within the org`() {
        // arrange: two org users have a @test.local email (Riccardo, Samuele)
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10, search = "test.local")

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertEquals(2, result.totalElements)
        Assertions.assertTrue(result.content.all { it.email.contains("test.local") })
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - search is scoped to the caller's organization`() {
        // arrange: "alice" exists but in a different org (Tech Solutions Inc.)
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10, search = "alice")

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        Assertions.assertEquals(0, result.totalElements)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - sorts by email ascending`() {
        // arrange
        setupJwtAuthentication("a1b2c3d4-e5f6-7890-abcd-ef1234567890", listOf("admin"))
        val filtersDTO = FiltersDTO(
            pageNumber = 0,
            pageSize = 10,
            sortField = "email",
            sortDirection = org.springframework.data.domain.Sort.Direction.ASC,
        )

        // act
        val result = userService.getUsers(filtersDTO)

        // assert: emails come back in ascending order
        val emails = result.content.map { it.email }
        Assertions.assertEquals(emails.sorted(), emails)
    }

    @Test
    @WithMockUser(roles = ["serviceaccount-fileservice"])
    fun `getUsers() - service account sees users across organizations`() {
        // arrange: service account is not org-scoped, so it sees every user
        setupJwtAuthentication("service-account", listOf("serviceaccount-fileservice"))
        val filtersDTO = FiltersDTO(pageNumber = 0, pageSize = 10)

        // act
        val result = userService.getUsers(filtersDTO)

        // assert: all six seeded users, spanning multiple organizations
        Assertions.assertEquals(6, result.totalElements)
        Assertions.assertTrue(result.content.map { it.organization }.distinct().size > 1)
    }

    @Test
    @WithMockUser(roles = ["staff", "manager"])
    fun `getUser() - successful for staff and manager`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")

        // act
        val result = userService.getUser(userId)

        // assert
        Assertions.assertEquals(userId, result.id)
        Assertions.assertEquals("mario.rossi@gmail.com", result.email)
        Assertions.assertEquals("Polytechnic University of Turin", result.organization)
        Assertions.assertEquals("Engineering", result.department)
        Assertions.assertEquals(UserRole.ROLE_manager, result.role)
        Assertions.assertEquals(10737418240, result.storageQuota)
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "admin"])
    fun `getUser() - successful for admin`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")

        // act
        val result = userService.getUser(userId)

        // assert
        Assertions.assertEquals(userId, result.id)
        Assertions.assertEquals("mario.rossi@gmail.com", result.email)
        Assertions.assertEquals("Polytechnic University of Turin", result.organization)
        Assertions.assertEquals("Engineering", result.department)
        Assertions.assertEquals(UserRole.ROLE_manager, result.role)
        Assertions.assertEquals(10737418240, result.storageQuota)
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "serviceaccount-fileservice"])
    fun `getUser() - successful for serviceaccount-fileservice`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")

        // act
        val result = userService.getUser(userId)

        // assert
        Assertions.assertEquals(userId, result.id)
        Assertions.assertEquals("mario.rossi@gmail.com", result.email)
        Assertions.assertEquals("Polytechnic University of Turin", result.organization)
        Assertions.assertEquals("Engineering", result.department)
        Assertions.assertEquals(UserRole.ROLE_manager, result.role)
        Assertions.assertEquals(10737418240, result.storageQuota)
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "admin", "serviceaccount-fileservice"])
    fun `getUser() - user not found`() {
        // arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440999")

        // act & assert
        assertThrows<UserNotFoundException> {
            userService.getUser(userId)
        }
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "admin", "serviceaccount-fileservice"])
    fun `getUser() - invalid UUID format`() {
        // arrange
        val invalidUserId = "invalid-uuid-format"

        // act & assert
        assertThrows<IllegalArgumentException> {
            userService.getUser(UUID.fromString(invalidUserId))
        }
    }

    @Test
    @WithMockUser(roles = ["staff", "manager"])
    fun `createUser() - forbidden for staff and manager`() {
        // arrange
        val user = UserCreationDTO(
            username = "Franco.Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        // act & assert
        assertThrows<AccessDeniedException> {
            userService.createUser(user)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `createUser() - successful creation`() {
        // arrange
        val user = UserCreationDTO(
            username = "Franco.Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val newUserId = UUID.randomUUID()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()
        val roleResource = mockk<RoleResource>()
        val realmRolesResource = mockk<RoleMappingResource>()
        val realmLevelRolesResource = mockk<RoleScopeResource>()

        val response = mockk<Response>()

        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("staff") } returns roleResource
        every { roleResource.toRepresentation() } returns RoleRepresentation().apply { name = "staff" }
        every { userResource.roles() } returns realmRolesResource
        every { realmRolesResource.realmLevel() } returns realmLevelRolesResource
        justRun { realmLevelRolesResource.add(any()) }

        // act
        userService.createUser(user)

        // assert
        verify(exactly = 1) { usersResource.create(any()) }
        verify(exactly = 1) { realmLevelRolesResource.add(any()) }

        // Verify user exists in DB
        val createdUser = userService.getUser(newUserId)
        Assertions.assertEquals(user.email, createdUser.email)
        Assertions.assertEquals(user.organization, createdUser.organization)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `createUser() - keycloak creation failure`() {
        // arrange
        val user = UserCreationDTO(
            username = "Franco.Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val response = mockk<Response>()

        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 409 // Conflict
        every { response.statusInfo } returns Response.Status.CONFLICT

        // act & assert
        assertThrows<RuntimeException> {
            userService.createUser(user)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `createUser() - role assignment failure triggers rollback`() {
        // arrange
        val user = UserCreationDTO(
            username = "Franco.Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val newUserId = UUID.randomUUID()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()

        val response = mockk<Response>()

        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("staff") } throws RuntimeException("Role not found")
        justRun { userResource.remove() }

        // act & assert
        assertThrows<RuntimeException> {
            userService.createUser(user)
        }

        // Verify rollback: user removed from Keycloak
        verify(exactly = 1) { userResource.remove() }
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "accountservice-fileservice"])
    fun `updateUser() - forbidden`() {
        // arrange
        val user = UserDTO(
            id = UUID.randomUUID(),
            fullName = "Franco Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 13214233
        )
        // act & assert
        assertThrows<AccessDeniedException> {
            userService.updateUser(user)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - user not found`() {
        // arrange
        val user = UserDTO(
            id = UUID.randomUUID(),
            fullName = "Franco Bollo",
            email = "franco.bollo@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 13214233
        )

        // act & assert
        assertThrows<UserNotFoundException> {
            userService.updateUser(user)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - invalid email`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val updatedUser = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "alice.johnson@gmail.com",
            organization = "Polytechnic University of Turin Updated",
            department = "Engineering Updated",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        // act & assert
        assertThrows<UserConflictError> {
            userService.updateUser(updatedUser)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - successful update`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val updatedUser = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi.update@gmail.com",
            organization = "Polytechnic University of Turin Updated",
            department = "Engineering Updated",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )
        mockKeycloakUserUpdate(userId)

        // act
        userService.updateUser(updatedUser)

        // assert
        val result = userService.getUser(userId)
        Assertions.assertEquals(updatedUser.fullName, result.fullName)
        Assertions.assertEquals(updatedUser.email, result.email)
        Assertions.assertEquals(updatedUser.organization, result.organization)
        Assertions.assertEquals(updatedUser.department, result.department)
        Assertions.assertEquals(updatedUser.role, result.role)
        Assertions.assertEquals(updatedUser.storageQuota, result.storageQuota)
    }


    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - throws when storage quota below current used`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        jdbcTemplate.update("UPDATE users SET storage_used = ? WHERE id = ?", 5_000_000L, userId)

        val updatedUser = UserDTO(
            id = userId,
            fullName = "Mario Rossi",
            email = "mario.rossi@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = 1_000L // below the 5_000_000 already used
        )

        // act & assert
        assertThrows<StorageQuotaBelowUsedException> {
            userService.updateUser(updatedUser)
        }

        // quota left unchanged in DB
        val storedQuota = jdbcTemplate.queryForObject(
            "SELECT storage_quota FROM users WHERE id = ?", Long::class.java, userId
        )
        Assertions.assertEquals(10737418240L, storedQuota)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - succeeds when storage quota above current used and keeps used unchanged`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        jdbcTemplate.update("UPDATE users SET storage_used = ? WHERE id = ?", 1_000L, userId)

        val updatedUser = UserDTO(
            id = userId,
            fullName = "Mario Rossi",
            email = "mario.rossi@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = 2_000L
        )
        mockKeycloakUserUpdate(userId)

        // act
        userService.updateUser(updatedUser)

        // assert
        val result = userService.getUser(userId)
        Assertions.assertEquals(2_000L, result.storageQuota)
        Assertions.assertEquals(1_000L, result.storageUsed) // update must not touch used
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - allows clearing the quota even with positive used`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        jdbcTemplate.update("UPDATE users SET storage_used = ? WHERE id = ?", 9_999L, userId)

        val updatedUser = UserDTO(
            id = userId,
            fullName = "Mario Rossi",
            email = "mario.rossi@gmail.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = null
        )
        mockKeycloakUserUpdate(userId)

        // act
        userService.updateUser(updatedUser)

        // assert
        val result = userService.getUser(userId)
        Assertions.assertNull(result.storageQuota)
        Assertions.assertEquals(9_999L, result.storageUsed)
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "serviceaccount-fileservice"])
    fun `deleteUser() - forbidden`() {
        // arrange
        val userId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")

        // act & assert
        assertThrows<AccessDeniedException> {
            userService.deleteUser(userId)
        }
    }

    @Test
    @WithMockUser(roles = ["staff", "manager", "admin"])
    fun `deleteUser() - user not found`() {
        // arrange
        val userId = UUID.randomUUID()
        setupJwtAuthentication("70c8c455-fbf3-4033-a4b0-21780006cf9f", listOf("admin"))

        // act & assert
        assertThrows<UserNotFoundException> {
            userService.deleteUser(userId)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - cannot delete own account`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        setupJwtAuthentication(userId.toString(), listOf("admin"))

        // act & assert
        assertThrows<SelfDeletionNotAllowedException> {
            userService.deleteUser(userId)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - successful deletion`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val authenticatedUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        setupJwtAuthentication(authenticatedUserId.toString(), listOf("admin"))

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()

        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        justRun { userResource.remove() }

        // act
        userService.deleteUser(userId)

        // assert
        verify(exactly = 1) { userResource.remove() }
        assertThrows<UserNotFoundException> {
            userService.getUser(userId)
        }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - keycloak failure triggers rollback`() {
        // arrange
        val userId = UUID.fromString("70c8c455-fbf3-4033-a4b0-21780006cf9f")
        val authenticatedUserId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
        setupJwtAuthentication(authenticatedUserId.toString(), listOf("admin"))

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()

        every { keycloak.realm(realm) } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        every { userResource.remove() } throws RuntimeException("Keycloak error")

        // act & assert
        assertThrows<RuntimeException> {
            userService.deleteUser(userId)
        }

        // verify user still exists in DB (rollback worked)
        val user = userService.getUser(userId)
        Assertions.assertEquals(userId, user.id)
    }
}