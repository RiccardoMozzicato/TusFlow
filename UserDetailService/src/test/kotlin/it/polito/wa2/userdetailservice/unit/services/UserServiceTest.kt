package it.polito.wa2.userdetailservice.unit.services

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.StorageQuotaBelowUsedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.repositories.UserRepository
import it.polito.wa2.userdetailservice.services.UserService
import jakarta.ws.rs.core.Response
import junit.framework.TestCase
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
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
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import java.net.URI
import java.time.Instant
import java.util.Date
import java.util.Optional
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals

class UserServiceTest {
    private lateinit var userRepo: UserRepository
    private lateinit var keycloak: Keycloak
    private lateinit var userService: UserService

    private val users: List<User> = listOf(
        User(
            id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_manager,
            department = "Security",
            storageQuota = null,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        ),
        User(
            id = UUID.fromString("123e4567-e89b-12d3-a456-426614174001"),
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = null,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )
    )

    @BeforeEach
    fun setup() {
        userRepo = mockk()
        keycloak = mockk()
        userService = UserService(userRepo, "file-management", keycloak)
    }

    @Test
    fun `getUsers() - successful`() {
        // arrange
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val filtersDTO: FiltersDTO = FiltersDTO(pageNumber = 0, pageSize = 10)
        val pageable = PageRequest.of(filtersDTO.pageNumber, filtersDTO.pageSize)
        val page: Page<User> = PageImpl(users, pageable, users.size.toLong())

        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()
        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { authentication.authorities } returns emptyList()
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        every { userRepo.findById(authenticatedUserId) } returns Optional.of(users[0])
        every { userRepo.findByOrganization("CloudFlare", pageable) } returns page

        // act
        val result = userService.getUsers(filtersDTO)

        // assert
        assertEquals(2, result.totalElements)
        TestCase.assertTrue(result.content.all { it is UserDTO })
        assertEquals("John Doe", result.content[0].fullName)
        assertEquals("Mary Smith", result.content[1].fullName)
        verify { userRepo.findById(authenticatedUserId) }
        verify { userRepo.findByOrganization("CloudFlare", pageable) }
    }

    @Test
    fun `getUser() - successful`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val user: User = users[0]
        every { userRepo.findById(userId) } returns Optional.of(user)

        // act
        val result = userService.getUser(userId)

        // assert
        assertEquals(userId, result.id)
        assertEquals("John Doe", result.fullName)
        verify { userRepo.findById(userId) }
    }

    @Test
    fun `getUser() - not found`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174999")
        every { userRepo.findById(userId) } returns Optional.empty()

        // act & assert
        Assertions.assertThrows(UserNotFoundException::class.java) {
            userService.getUser(userId)
        }
    }

    @Test
    fun `createUser() - successful creation with staff role`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "john.doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val newUserId = UUID.randomUUID()
        val userRepSlot = slot<UserRepresentation>()
        val userSlot = slot<User>()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()
        val roleResource = mockk<RoleResource>()
        val roleMappingResource = mockk<RoleMappingResource>()
        val roleScopeResource = mockk<RoleScopeResource>()
        val response = mockk<Response>()

        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(capture(userRepSlot)) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("staff") } returns roleResource
        every { roleResource.toRepresentation() } returns RoleRepresentation().apply { name = "staff" }
        every { userResource.roles() } returns roleMappingResource
        every { roleMappingResource.realmLevel() } returns roleScopeResource
        justRun { roleScopeResource.add(any()) }
        every { userRepo.save(capture(userSlot)) } answers { userSlot.captured }

        // act
        userService.createUser(userCreationDTO)

        // assert
        verify { usersResource.create(any()) }
        verify { roleScopeResource.add(any()) }
        verify { userRepo.save(any()) }

        // Verify UserRepresentation
        assertEquals("john.doe", userRepSlot.captured.username)
        assertEquals("john.doe@example.com", userRepSlot.captured.email)
        TestCase.assertTrue(userRepSlot.captured.isEnabled)

        // Verify saved User
        assertEquals(newUserId, userSlot.captured.id)
        assertEquals("john.doe", userSlot.captured.fullName)
        assertEquals("john.doe@example.com", userSlot.captured.email)
        assertEquals(UserRole.ROLE_staff, userSlot.captured.role)
    }

    @Test
    fun `createUser() - successful creation with admin role`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "admin.user",
            email = "admin@example.com",
            organization = "CloudFlare",
            department = "Management",
            role = UserRole.ROLE_admin,
            storageQuota = 10737418240
        )

        val newUserId = UUID.randomUUID()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()
        val roleResource = mockk<RoleResource>()
        val roleMappingResource = mockk<RoleMappingResource>()
        val roleScopeResource = mockk<RoleScopeResource>()
        val response = mockk<Response>()

        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("admin") } returns roleResource
        every { roleResource.toRepresentation() } returns RoleRepresentation().apply { name = "admin" }
        every { userResource.roles() } returns roleMappingResource
        every { roleMappingResource.realmLevel() } returns roleScopeResource
        justRun { roleScopeResource.add(any()) }
        every { userRepo.save(any()) } answers { firstArg() }

        // act
        userService.createUser(userCreationDTO)

        // assert
        verify { rolesResource.get("admin") }
        verify { userRepo.save(any()) }
    }

    @Test
    fun `createUser() - keycloak creation fails with 409 conflict`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "existing.user",
            email = "existing@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val response = mockk<Response>()

        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 409
        every { response.statusInfo } returns Response.Status.CONFLICT

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.createUser(userCreationDTO)
        }

        TestCase.assertTrue(exception.message!!.contains("Error on user creation in Keycloak"))
        verify(exactly = 0) { userRepo.save(any()) }
    }

    @Test
    fun `createUser() - role assignment fails and triggers rollback`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "john.doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_manager,
            storageQuota = 5368709120
        )

        val newUserId = UUID.randomUUID()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()
        val response = mockk<Response>()

        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("manager") } throws RuntimeException("Role not found")
        justRun { userResource.remove() }

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.createUser(userCreationDTO)
        }

        TestCase.assertTrue(exception.message!!.contains("Error on assigning the role"))
        verify(exactly = 1) { userResource.remove() } // Rollback called
        verify(exactly = 0) { userRepo.save(any()) } // DB save not called
    }

    @Test
    fun `createUser() - database save fails and triggers keycloak rollback`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "john.doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        val newUserId = UUID.randomUUID()

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()
        val rolesResource = mockk<RolesResource>()
        val roleResource = mockk<RoleResource>()
        val roleMappingResource = mockk<RoleMappingResource>()
        val roleScopeResource = mockk<RoleScopeResource>()
        val response = mockk<Response>()

        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.create(any()) } returns response
        every { response.status } returns 201
        every { response.statusInfo } returns Response.Status.CREATED
        every { response.location } returns URI.create("http://localhost:9090/admin/realms/file-management/users/$newUserId")
        every { usersResource.get(newUserId.toString()) } returns userResource
        every { realmResource.roles() } returns rolesResource
        every { rolesResource.get("staff") } returns roleResource
        every { roleResource.toRepresentation() } returns RoleRepresentation().apply { name = "staff" }
        every { userResource.roles() } returns roleMappingResource
        every { roleMappingResource.realmLevel() } returns roleScopeResource
        justRun { roleScopeResource.add(any()) }
        every { userRepo.save(any()) } throws RuntimeException("Database constraint violation")
        justRun { userResource.remove() }

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.createUser(userCreationDTO)
        }

        TestCase.assertTrue(exception.message!!.contains("Error on local save"))
        verify(exactly = 1) { userResource.remove() } // Keycloak rollback called
    }

    @Test
    fun `updateUser() - successful update`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.smith@gmail.com",
            organization = "NewOrg",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)
        every { userRepo.getUserByEmail("john.smith@gmail.com") } returns Optional.empty()

        // act
        userService.updateUser(updatedUserDTO)

        // assert
        assertEquals("John Smith", existingUser.fullName)
        assertEquals("john.smith@gmail.com", existingUser.email)
        assertEquals("NewOrg", existingUser.organization)
        assertEquals("Engineering", existingUser.department)
        assertEquals(UserRole.ROLE_manager, existingUser.role)
        assertEquals(10737418240, existingUser.storageQuota)
        verify { userRepo.findById(userId) }
        verify { userRepo.getUserByEmail("john.smith@gmail.com") }
    }

    @Test
    fun `updateUser() - successful update with same email`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.doe@gmail.com", // Same email
            organization = "NewOrg",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)

        // act
        userService.updateUser(updatedUserDTO)

        // assert
        assertEquals("John Smith", existingUser.fullName)
        assertEquals("john.doe@gmail.com", existingUser.email) // Email unchanged
        assertEquals("NewOrg", existingUser.organization)
        assertEquals(UserRole.ROLE_manager, existingUser.role)
        verify { userRepo.findById(userId) }
        verify(exactly = 0) { userRepo.getUserByEmail(any()) } // Email check not called
    }

    @Test
    fun `updateUser() - user not found`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174999")
        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.smith@gmail.com",
            organization = "NewOrg",
            department = "Engineering",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        every { userRepo.findById(userId) } returns Optional.empty()

        // act & assert
        val exception = Assertions.assertThrows(UserNotFoundException::class.java) {
            userService.updateUser(updatedUserDTO)
        }

        TestCase.assertTrue(exception.message!!.contains("User with id $userId not found"))
        verify { userRepo.findById(userId) }
        verify(exactly = 0) { userRepo.getUserByEmail(any()) }
    }

    @Test
    fun `updateUser() - email conflict with another user`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val anotherUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")

        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val anotherUser = User(
            id = anotherUserId,
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "mary.smith@gmail.com", // Email already exists for another user
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)
        every { userRepo.getUserByEmail("mary.smith@gmail.com") } returns Optional.of(anotherUser)

        // act & assert
        val exception = Assertions.assertThrows(UserConflictError::class.java) {
            userService.updateUser(updatedUserDTO)
        }

        TestCase.assertTrue(exception.message!!.contains("Another user with that email already exists"))
        assertEquals("john.doe@gmail.com", existingUser.email) // Email not changed
        verify { userRepo.findById(userId) }
        verify { userRepo.getUserByEmail("mary.smith@gmail.com") }
    }

    @Test
    fun `updateUser() - only update specific fields`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Doe", // Same
            email = "john.doe@gmail.com", // Same
            organization = "CloudFlare", // Same
            department = "Security", // Changed
            role = UserRole.ROLE_manager, // Changed
            storageQuota = 10737418240 // Changed
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)

        // act
        userService.updateUser(updatedUserDTO)

        // assert
        assertEquals("John Doe", existingUser.fullName) // Unchanged
        assertEquals("john.doe@gmail.com", existingUser.email) // Unchanged
        assertEquals("CloudFlare", existingUser.organization) // Unchanged
        assertEquals("Security", existingUser.department) // Changed
        assertEquals(UserRole.ROLE_manager, existingUser.role) // Changed
        assertEquals(10737418240, existingUser.storageQuota) // Changed
        verify { userRepo.findById(userId) }
    }

    @Test
    fun `updateUser() - throws when storage quota is below current used`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 10000,
            storageUsed = 5000,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com", // same email to skip conflict lookup
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 1000 // below the 5000 already used
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)

        // act & assert
        val exception = Assertions.assertThrows(StorageQuotaBelowUsedException::class.java) {
            userService.updateUser(updatedUserDTO)
        }
        TestCase.assertTrue(exception.message!!.contains("5000"))
        // entity must be left untouched
        assertEquals(10000, existingUser.storageQuota)
    }

    @Test
    fun `updateUser() - allows storage quota equal to current used`() {
        // arrange - boundary: quota == used is acceptable
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 10000,
            storageUsed = 1000,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 1000 // exactly the used amount
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)

        // act
        userService.updateUser(updatedUserDTO)

        // assert
        assertEquals(1000, existingUser.storageQuota)
    }

    @Test
    fun `updateUser() - allows null storage quota even when storage used is positive`() {
        // arrange - clearing the quota disables enforcement but usage keeps being tracked
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")
        val existingUser = User(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 10000,
            storageUsed = 5000,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        val updatedUserDTO = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@gmail.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = null
        )

        every { userRepo.findById(userId) } returns Optional.of(existingUser)

        // act
        userService.updateUser(updatedUserDTO)

        // assert - quota cleared, used untouched
        assertEquals(null, existingUser.storageQuota)
        assertEquals(5000, existingUser.storageUsed)
    }

    @Test
    fun `deleteUser() - successful deletion`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val userToDelete = User(
            id = userId,
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        // Mock JWT authentication
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()

        every { userRepo.findById(userId) } returns Optional.of(userToDelete)
        justRun { userRepo.deleteById(userId) }
        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        justRun { userResource.remove() }

        // act
        userService.deleteUser(userId)

        // assert
        verify { userRepo.findById(userId) }
        verify { userRepo.deleteById(userId) }
        verify { userResource.remove() }
        verify(exactly = 0) { userRepo.save(any()) } // No rollback needed
    }

    @Test
    fun `deleteUser() - cannot delete own account`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        // Mock JWT authentication with same user ID
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns userId.toString()
        SecurityContextHolder.setContext(securityContext)

        // act & assert
        val exception = Assertions.assertThrows(SelfDeletionNotAllowedException::class.java) {
            userService.deleteUser(userId)
        }

        TestCase.assertTrue(exception.message!!.contains("Cannot delete your own user account"))
        verify(exactly = 0) { userRepo.findById(any()) }
        verify(exactly = 0) { userRepo.deleteById(any()) }
    }

    @Test
    fun `deleteUser() - user not found`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174999")
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        // Mock JWT authentication
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        every { userRepo.findById(userId) } returns Optional.empty()

        // act & assert
        val exception = Assertions.assertThrows(UserNotFoundException::class.java) {
            userService.deleteUser(userId)
        }

        TestCase.assertTrue(exception.message!!.contains("User with id $userId not found"))
        verify { userRepo.findById(userId) }
        verify(exactly = 0) { userRepo.deleteById(any()) }
    }

    @Test
    fun `deleteUser() - database deletion fails`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val userToDelete = User(
            id = userId,
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        // Mock JWT authentication
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        every { userRepo.findById(userId) } returns Optional.of(userToDelete)
        every { userRepo.deleteById(userId) } throws RuntimeException("Database constraint violation")

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.deleteUser(userId)
        }

        TestCase.assertTrue(exception.message!!.contains("Error deleting user from database"))
        verify { userRepo.findById(userId) }
        verify { userRepo.deleteById(userId) }
        verify(exactly = 0) { keycloak.realm(any()) } // Keycloak not called
    }

    @Test
    fun `deleteUser() - keycloak deletion fails and triggers rollback`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val userToDelete = User(
            id = userId,
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        // Mock JWT authentication
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()

        every { userRepo.findById(userId) } returns Optional.of(userToDelete)
        justRun { userRepo.deleteById(userId) }
        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        every { userResource.remove() } throws RuntimeException("Keycloak connection error")
        every { userRepo.save(userToDelete) } returns userToDelete

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.deleteUser(userId)
        }

        TestCase.assertTrue(exception.message!!.contains("Error deleting user from Keycloak, rolled back database deletion"))
        verify { userRepo.findById(userId) }
        verify { userRepo.deleteById(userId) }
        verify { userResource.remove() }
        verify(exactly = 1) { userRepo.save(userToDelete) } // Rollback executed
    }

    @Test
    fun `deleteUser() - keycloak deletion fails and rollback also fails`() {
        // arrange
        val userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001")
        val authenticatedUserId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000")

        val userToDelete = User(
            id = userId,
            fullName = "Mary Smith",
            email = "mary.smith@gmail.com",
            organization = "CloudFlare",
            role = UserRole.ROLE_staff,
            department = "IT",
            storageQuota = 5368709120,
            createdAt = Date.from(Instant.now().minusSeconds(60 * 60 * 24)),
            updatedAt = Date.from(Instant.now().minusSeconds(60 * 60))
        )

        // Mock JWT authentication
        val jwt = mockk<Jwt>()
        val authentication = mockk<Authentication>()
        val securityContext = mockk<SecurityContext>()

        every { securityContext.authentication } returns authentication
        every { authentication.principal } returns jwt
        every { jwt.subject } returns authenticatedUserId.toString()
        SecurityContextHolder.setContext(securityContext)

        val realmResource = mockk<RealmResource>()
        val usersResource = mockk<UsersResource>()
        val userResource = mockk<UserResource>()

        every { userRepo.findById(userId) } returns Optional.of(userToDelete)
        justRun { userRepo.deleteById(userId) }
        every { keycloak.realm("file-management") } returns realmResource
        every { realmResource.users() } returns usersResource
        every { usersResource.get(userId.toString()) } returns userResource
        every { userResource.remove() } throws RuntimeException("Keycloak connection error")
        every { userRepo.save(userToDelete) } throws RuntimeException("Database rollback failed")

        // act & assert
        val exception = Assertions.assertThrows(RuntimeException::class.java) {
            userService.deleteUser(userId)
        }

        TestCase.assertTrue(exception.message!!.contains("Critical error: failed to rollback database deletion after Keycloak failure"))
        verify { userRepo.findById(userId) }
        verify { userRepo.deleteById(userId) }
        verify { userResource.remove() }
        verify(exactly = 1) { userRepo.save(userToDelete) } // Rollback attempted
    }
}