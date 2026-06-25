package it.polito.wa2.userdetailservice.unit.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import it.polito.wa2.userdetailservice.controllers.UserController
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.services.UserServiceInterface
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID

/**
 * Unit tests for UserController.
 * These tests verify controller logic only, with mocked service layer.
 * Security is not enforced here as the service is mocked - security is tested in integration tests.
 */
@WebMvcTest(UserController::class)
class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var userService: UserServiceInterface

    // GET /api/v1/users tests
    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers - successful with default pagination`() {
        // arrange
        val users = listOf(
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "John Doe",
                email = "john.doe@example.com",
                organization = "CloudFlare",
                department = "IT",
                role = UserRole.ROLE_staff,
                storageQuota = 5368709120
            ),
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "Mary Smith",
                email = "mary.smith@example.com",
                organization = "CloudFlare",
                department = "Engineering",
                role = UserRole.ROLE_manager,
                storageQuota = 10737418240
            )
        )
        val page = PageImpl(users, PageRequest.of(0, 10), users.size.toLong())

        every { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) } returns page

        // act & assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(2))
            .andExpect(jsonPath("$.content[0].fullName").value("John Doe"))
            .andExpect(jsonPath("$.content[1].fullName").value("Mary Smith"))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.size").value(10))
            .andExpect(jsonPath("$.number").value(0))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers - successful with custom pagination`() {
        // arrange
        val users = listOf(
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "John Doe",
                email = "john.doe@example.com",
                organization = "CloudFlare",
                department = "IT",
                role = UserRole.ROLE_staff,
                storageQuota = 5368709120
            )
        )
        val page = PageImpl(users, PageRequest.of(1, 5), 10)

        every { userService.getUsers(FiltersDTO(1, 5, null, "fullName")) } returns page

        // act & assert
        mockMvc.perform(
            get("/api/v1/users")
                .param("page", "1")
                .param("size", "5")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))
            .andExpect(jsonPath("$.totalElements").value(10))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.number").value(1))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(1, 5, null, "fullName")) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers - parses search and sort params into the FiltersDTO`() {
        // arrange
        val page = PageImpl(emptyList<UserDTO>(), PageRequest.of(0, 10), 0)
        every {
            userService.getUsers(FiltersDTO(0, 10, "rossi", "email", org.springframework.data.domain.Sort.Direction.DESC))
        } returns page

        // act & assert
        mockMvc.perform(
            get("/api/v1/users")
                .param("search", "rossi")
                .param("sort", "email,desc")
        )
            .andExpect(status().isOk)

        verify(exactly = 1) {
            userService.getUsers(FiltersDTO(0, 10, "rossi", "email", org.springframework.data.domain.Sort.Direction.DESC))
        }
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `getUsers - allowed for staff role (can read users)`() {
        // arrange
        val users = listOf(
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "John Doe",
                email = "john.doe@example.com",
                organization = "CloudFlare",
                department = "IT",
                role = UserRole.ROLE_staff,
                storageQuota = 5368709120
            )
        )
        val page = PageImpl(users, PageRequest.of(0, 10), 1)

        every { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) } returns page

        // act & assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) }
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `getUsers - allowed for manager role (can read users)`() {
        // arrange
        val users = listOf(
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "Jane Manager",
                email = "jane@example.com",
                organization = "CloudFlare",
                department = "Management",
                role = UserRole.ROLE_manager,
                storageQuota = 10737418240
            )
        )
        val page = PageImpl(users, PageRequest.of(0, 10), 1)

        every { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) } returns page

        // act & assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content.length()").value(1))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) }
    }

    @Test
    fun `getUsers - unauthorized without authentication`() {
        // act & assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isUnauthorized)

        verify(exactly = 0) { userService.getUsers(any()) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers - empty list`() {
        // arrange
        val page = PageImpl<UserDTO>(emptyList(), PageRequest.of(0, 10), 0)

        every { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) } returns page

        // act & assert
        mockMvc.perform(get("/api/v1/users"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(0))
            .andExpect(jsonPath("$.totalElements").value(0))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(0, 10, null, "fullName")) }
    }

    // GET /api/v1/users/{id} tests
    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUser - successful for admin`() {
        // arrange
        val userId = UUID.randomUUID()
        val user = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userService.getUser(userId) } returns user

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.fullName").value("John Doe"))
            .andExpect(jsonPath("$.email").value("john.doe@example.com"))

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["serviceaccount-fileservice"])
    fun `getUser - successful for serviceaccount-fileservice`() {
        // arrange
        val userId = UUID.randomUUID()
        val user = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userService.getUser(userId) } returns user

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `getUser - allowed for staff (can read users)`() {
        // arrange
        val userId = UUID.randomUUID()
        val user = UserDTO(
            id = userId,
            fullName = "John Doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userService.getUser(userId) } returns user

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUser - not found`() {
        // arrange
        val userId = UUID.randomUUID()

        every { userService.getUser(userId) } throws UserNotFoundException("User not found")

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId"))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.getUser(userId) }
    }

    // POST /api/v1/users tests
    @Test
    @WithMockUser(roles = ["admin"])
    fun `postUser - successful creation`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "john.doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userService.createUser(userCreationDTO) } returns Unit

        // act & assert
        mockMvc.perform(
            post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userCreationDTO))
        )
            .andExpect(status().isCreated)

        verify(exactly = 1) { userService.createUser(any()) }
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `postUser - calls service for staff (security enforced at service layer)`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "john.doe",
            email = "john.doe@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        // In unit tests, service is mocked so @PreAuthorize is not evaluated
        // Security is tested in integration tests
        every { userService.createUser(userCreationDTO) } returns Unit

        // act & assert
        mockMvc.perform(
            post("/api/v1/users")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userCreationDTO))
        )
            .andExpect(status().isCreated)

        verify(exactly = 1) { userService.createUser(any()) }
    }

    // PUT /api/v1/users/{id} tests
    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser - successful update`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.smith@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        justRun { userService.updateUser(userDTO) }

        // act & assert
        mockMvc.perform(
            put("/api/v1/users/$userId")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO))
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.updateUser(any()) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser - id mismatch`() {
        // arrange
        val pathId = UUID.randomUUID()
        val bodyId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = bodyId,
            fullName = "John Smith",
            email = "john.smith@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        // act & assert
        mockMvc.perform(
            put("/api/v1/users/$pathId")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO))
        )
            .andExpect(status().isConflict)

        verify(exactly = 0) { userService.updateUser(any()) }
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `updateUser - calls service for staff (security enforced at service layer)`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.smith@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        // In unit tests, service is mocked so @PreAuthorize is not evaluated
        justRun { userService.updateUser(userDTO) }

        // act & assert
        mockMvc.perform(
            put("/api/v1/users/$userId")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO))
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.updateUser(any()) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser - user not found`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "John Smith",
            email = "john.smith@example.com",
            organization = "CloudFlare",
            department = "IT",
            role = UserRole.ROLE_manager,
            storageQuota = 10737418240
        )

        every { userService.updateUser(userDTO) } throws UserNotFoundException("User not found")

        // act & assert
        mockMvc.perform(
            put("/api/v1/users/$userId")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(userDTO))
        )
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.updateUser(any()) }
    }

    // DELETE /api/v1/users/{id} tests
    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser - successful deletion`() {
        // arrange
        val userId = UUID.randomUUID()

        justRun { userService.deleteUser(userId) }

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `deleteUser - calls service for staff (security enforced at service layer)`() {
        // arrange
        val userId = UUID.randomUUID()

        // In unit tests, service is mocked so @PreAuthorize is not evaluated
        justRun { userService.deleteUser(userId) }

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `deleteUser - calls service for manager (security enforced at service layer)`() {
        // arrange
        val userId = UUID.randomUUID()

        // In unit tests, service is mocked so @PreAuthorize is not evaluated
        justRun { userService.deleteUser(userId) }

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser - user not found`() {
        // arrange
        val userId = UUID.randomUUID()

        every { userService.deleteUser(userId) } throws UserNotFoundException("User not found")

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }
}