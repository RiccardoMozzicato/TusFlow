package it.polito.wa2.userdetailservice.config

import io.mockk.*
import jakarta.ws.rs.core.Response
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.resource.*
import org.keycloak.representations.idm.RoleRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import java.net.URI
import java.util.UUID

@TestConfiguration
class KeycloakMockConfig {

    @Bean
    @Primary
    fun keycloakMock(): Keycloak {
        val keycloakMock = mockk<Keycloak>(relaxed = true)
        val mockRealmResource = mockk<RealmResource>(relaxed = true)
        val mockUsersResource = mockk<UsersResource>(relaxed = true)
        val mockRolesResource = mockk<RolesResource>(relaxed = true)
        val mockRoleMappingResource = mockk<RoleMappingResource>(relaxed = true)
        val mockRoleScopeResource = mockk<RoleScopeResource>(relaxed = true)

        // Default behavior for successful creation
        every { keycloakMock.realm(any()) } returns mockRealmResource
        every { mockRealmResource.users() } returns mockUsersResource
        every { mockRealmResource.roles() } returns mockRolesResource

        // Mock successful user creation with unique IDs
        every { mockUsersResource.create(any<UserRepresentation>()) } answers {
            val userRep = firstArg<UserRepresentation>()
            val mockId = UUID.randomUUID().toString()

            println("Mock Keycloak: Creating user with username=${userRep.username}, email=${userRep.email}")

            // Create the response mock with proper location header
            val locationUri = URI.create("http://localhost:9090/admin/realms/file-management/users/$mockId")
            val response = mockk<Response>()

            every { response.status } returns 201
            every { response.statusInfo } returns mockk(relaxed = true) {
                every { statusCode } returns 201
                every { reasonPhrase } returns "Created"
            }
            every { response.location } returns locationUri
            every { response.hasEntity() } returns false
            every { response.entity } returns null
            every { response.readEntity(any<Class<*>>()) } returns null
            every { response.close() } just Runs

            // Create a mock UserResource for this specific user
            val mockUserResource = mockk<UserResource>(relaxed = true)
            every { mockUserResource.toRepresentation() } returns userRep
            every { mockUserResource.roles() } returns mockRoleMappingResource
            every { mockUserResource.remove() } just Runs
            every { mockUsersResource.get(mockId) } returns mockUserResource

            println("Mock Keycloak: User created with ID: $mockId, Location: $locationUri")
            response
        }

        every { mockRoleMappingResource.realmLevel() } returns mockRoleScopeResource
        every { mockRoleScopeResource.add(any()) } just Runs
        every { mockRoleScopeResource.remove(any()) } just Runs

        // Handle different roles dynamically
        every { mockRolesResource.get(any()) } answers {
            val roleName = firstArg<String>()
            println("Mock Keycloak: Getting role: $roleName")

            val mockRoleResource = mockk<RoleResource>(relaxed = true)
            every { mockRoleResource.toRepresentation() } answers {
                RoleRepresentation().apply {
                    id = UUID.randomUUID().toString()
                    name = roleName
                }
            }
            mockRoleResource
        }

        return keycloakMock
    }
}
