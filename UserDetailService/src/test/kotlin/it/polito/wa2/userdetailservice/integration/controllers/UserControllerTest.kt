package it.polito.wa2.userdetailservice.integration.controllers

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.justRun
import io.mockk.verify
import it.polito.wa2.userdetailservice.IntegrationTest
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.services.UserService
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.data.domain.PageImpl
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.DatabasePopulatorUtils
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.util.UUID
import javax.sql.DataSource

@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UserControllerTest: IntegrationTest() {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
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

    // GET /api/v1/users tests
    @Test
    fun `getUsers() - unauthorized without auth`() {
        mockMvc.perform(get("/api/v1/users").with(csrf()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `getUsers() - forbidden for staff role`() {
        mockMvc.perform(get("/api/v1/users").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `getUsers() - forbidden for manager role`() {

        mockMvc.perform(get("/api/v1/users").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUsers() - successful with custom pagination`() {
        // arrange
        val users = listOf(
            UserDTO(
                id = UUID.randomUUID(),
                fullName = "Mario Rossi",
                email = "mario.rossi@example.com",
                organization = "Polytechnic University of Turin",
                department = "Engineering",
                role = UserRole.ROLE_staff,
                storageQuota = 10737418240
            )
        )
        val page = PageImpl(users)

        every { userService.getUsers(FiltersDTO(0, 2)) } returns page

        // act & assert
        mockMvc.perform(get("/api/v1/users")
            .param("page", "0")
            .param("size", "2")
            .with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)
            .andExpect(jsonPath("$.content.length()").value(1))

        verify(exactly = 1) { userService.getUsers(FiltersDTO(0, 2)) }
    }

    // GET /api/v1/users/{id} tests
    @Test
    fun `getUser() - unauthorized without auth`() {
        val userId = UUID.randomUUID()

        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `getUser() - forbidden for staff role`() {
        val userId = UUID.randomUUID()

        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `getUser() - forbidden for manager role`() {
        val userId = UUID.randomUUID()


        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUser() - successful for admin`() {
        // arrange
        val userId = UUID.randomUUID()
        val user = UserDTO(
            id = userId,
            fullName = "Mario Rossi",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 10737418240
        )

        every { userService.getUser(userId) } returns user

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.fullName").value("Mario Rossi"))
            .andExpect(jsonPath("$.email").value("mario.rossi@example.com"))
            .andExpect(jsonPath("$.organization").value("Polytechnic University of Turin"))
            .andExpect(jsonPath("$.department").value("Engineering"))
            .andExpect(jsonPath("$.role").value("ROLE_staff"))
            .andExpect(jsonPath("$.storageQuota").value(10737418240))

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["serviceaccount-fileservice"])
    fun `getUser() - successful for serviceaccount-fileservice`() {
        // arrange
        val userId = UUID.randomUUID()
        val user = UserDTO(
            id = userId,
            fullName = "Mario Rossi",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_staff,
            storageQuota = 10737418240
        )

        every { userService.getUser(userId) } returns user

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId.toString()))

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUser() - not found`() {
        // arrange
        val userId = UUID.randomUUID()

        every { userService.getUser(userId) } throws UserNotFoundException("User with id $userId not found")

        // act & assert
        mockMvc.perform(get("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.getUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `getUser() - invalid UUID`() {
        // act & assert
        mockMvc.perform(get("/api/v1/users/invalid-uuid").with(csrf()))
            .andExpect(status().isBadRequest)
    }

    // POST /api/v1/users tests
    @Test
    fun `postUser() - unauthorized without auth`() {
        val userCreationDTO = UserCreationDTO(
            username = "luigi.bianchi",
            email = "luigi.bianchi@example.com",
            organization = "Polytechnic University of Milan",
            department = "Computer Science",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userCreationDTO))
            .with(csrf()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `postUser() - forbidden for staff role`() {
        val userCreationDTO = UserCreationDTO(
            username = "luigi.bianchi",
            email = "luigi.bianchi@example.com",
            organization = "Polytechnic University of Milan",
            department = "Computer Science",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userCreationDTO))
            .with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `postUser() - forbidden for manager role`() {
        val userCreationDTO = UserCreationDTO(
            username = "luigi.bianchi",
            email = "luigi.bianchi@example.com",
            organization = "Polytechnic University of Milan",
            department = "Computer Science",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userCreationDTO))
            .with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `postUser() - successful creation`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "luigi.bianchi",
            email = "luigi.bianchi@example.com",
            organization = "Polytechnic University of Milan",
            department = "Computer Science",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        justRun { userService.createUser(userCreationDTO) }

        // act & assert
        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userCreationDTO))
            .with(csrf()))
            .andExpect(status().isCreated)

        verify(exactly = 1) { userService.createUser(userCreationDTO) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `postUser() - conflict - email already exists`() {
        // arrange
        val userCreationDTO = UserCreationDTO(
            username = "duplicate.user",
            email = "existing@example.com",
            organization = "Polytechnic University of Milan",
            department = "Computer Science",
            role = UserRole.ROLE_staff,
            storageQuota = 5368709120
        )

        every { userService.createUser(userCreationDTO) } throws UserConflictError("User with this email already exists")

        // act & assert
        mockMvc.perform(post("/api/v1/users")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userCreationDTO))
            .with(csrf()))
            .andExpect(status().isConflict)

        verify(exactly = 1) { userService.createUser(userCreationDTO) }
    }

    // PUT /api/v1/users/{id} tests
    @Test
    fun `updateUser() - unauthorized without auth`() {
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `updateUser() - forbidden for staff role`() {
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `updateUser() - forbidden for manager role`() {
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - successful update`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi.updated@example.com",
            organization = "Polytechnic University of Turin Updated",
            department = "Engineering Updated",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        justRun { userService.updateUser(userDTO) }

        // act & assert
        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.updateUser(userDTO) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - not found`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        every { userService.updateUser(userDTO) } throws UserNotFoundException("User with id $userId not found")

        // act & assert
        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.updateUser(userDTO) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - email conflict`() {
        // arrange
        val userId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = userId,
            fullName = "Mario Rossi Updated",
            email = "existing@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        every { userService.updateUser(userDTO) } throws UserConflictError("Another user with that email already exists")

        // act & assert
        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isConflict)

        verify(exactly = 1) { userService.updateUser(userDTO) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - invalid UUID`() {
        // arrange
        val userDTO = UserDTO(
            id = UUID.randomUUID(),
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        // act & assert
        mockMvc.perform(put("/api/v1/users/invalid-uuid")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `updateUser() - id mismatch`() {
        // arrange
        val userId = UUID.randomUUID()
        val differentId = UUID.randomUUID()
        val userDTO = UserDTO(
            id = differentId,
            fullName = "Mario Rossi Updated",
            email = "mario.rossi@example.com",
            organization = "Polytechnic University of Turin",
            department = "Engineering",
            role = UserRole.ROLE_admin,
            storageQuota = 21474836480
        )

        // act & assert
        mockMvc.perform(put("/api/v1/users/$userId")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(userDTO))
            .with(csrf()))
            .andExpect(status().isConflict)
    }



    // DELETE /api/v1/users/{id} tests
    @Test
    fun `deleteUser() - unauthorized without auth`() {
        val userId = UUID.randomUUID()

        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isUnauthorized)
    }

    @Test
    @WithMockUser(roles = ["staff"])
    fun `deleteUser() - forbidden for staff role`() {
        val userId = UUID.randomUUID()

        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["manager"])
    fun `deleteUser() - forbidden for manager role`() {
        val userId = UUID.randomUUID()

        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isForbidden)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - successful deletion`() {
        // arrange
        val userId = UUID.randomUUID()

        justRun { userService.deleteUser(userId) }

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNoContent)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - not found`() {
        // arrange
        val userId = UUID.randomUUID()

        every { userService.deleteUser(userId) } throws UserNotFoundException("User with id $userId not found")

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isNotFound)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }

    @Test
    @WithMockUser(username="admin", roles = ["admin"])
    fun `deleteUser() - invalid UUID`() {
        // act & assert
        mockMvc.perform(delete("/api/v1/users/invalid-uuid")
            .with(csrf()))
            .andExpect(status().isBadRequest)
    }

    @Test
    @WithMockUser(roles = ["admin"])
    fun `deleteUser() - cannot delete own account`() {
        // arrange
        val userId = UUID.randomUUID()

        every { userService.deleteUser(userId) } throws SelfDeletionNotAllowedException("Cannot delete your own account")

        // act & assert
        mockMvc.perform(delete("/api/v1/users/$userId").with(csrf()))
            .andExpect(status().isForbidden)

        verify(exactly = 1) { userService.deleteUser(userId) }
    }
}
