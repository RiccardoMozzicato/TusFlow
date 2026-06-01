package it.polito.wa2.userdetailservice.services

import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.dto.toDTO
import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.StorageQuotaBelowUsedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.repositories.UserRepository
import jakarta.transaction.Transactional
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID

@Service
class UserService(
    private var userRepo: UserRepository,
    @Value("\${keycloak-admin.realm}") private val realm: String,
    private val keycloak: Keycloak, // Il tuo bean configurato
): UserServiceInterface {

    val log = LoggerFactory.getLogger(UserService::class.java)

    private fun getAuthenticatedUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = authentication.principal as Jwt
        return UUID.fromString(jwt.subject)
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUsers(filtersDTO: FiltersDTO): Page<UserDTO> {
        val pageable = PageRequest.of(filtersDTO.pageNumber, filtersDTO.pageSize)

        // Service accounts have no row in userRepo, so skip the org-scoped lookup
        val isServiceAccount = SecurityContextHolder.getContext().authentication.authorities
            .any { it.authority == "ROLE_serviceaccount-fileservice" }

        if (isServiceAccount) {
            return userRepo.findAll(pageable).map { it.toDTO() }
        }

        val authenticatedUserId = getAuthenticatedUserId()
        val authenticatedUser = userRepo.findById(authenticatedUserId).orElseThrow {
            UserNotFoundException("The user with id $authenticatedUserId not found")
        }
        val users = userRepo.findByOrganization(authenticatedUser.organization, pageable)

        return users.map { it.toDTO() }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUser(id: UUID): UserDTO {
        val user: User = userRepo.findById(id).orElseThrow{
            UserNotFoundException("The user with id $id not found")
        }
        return user.toDTO()
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUsersBatch(ids: List<UUID>): Map<UUID, UserDTO> {
        log.debug("Fetching batch of {} users", ids.size)
        val users = userRepo.findAllById(ids)
        return users.associate { it.id to it.toDTO() }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun createUser(user: UserCreationDTO) {

        log.debug("Creating user: {}", user.toString())
        // 1. Preparing UserRepresentation for keycloak
        val userRep = UserRepresentation().apply {
            username = user.username
            email = user.email
            isEnabled = true
            isEmailVerified = false // O true, a seconda delle tue policy
        }

        // 2. Preparing Credentials for keycloak
        val cred = CredentialRepresentation().apply {
            type = CredentialRepresentation.PASSWORD
            value= "temporary"
            isTemporary = true
        }
        userRep.credentials = listOf(cred)

        // 3. This is the UsersResource for the realm
        val usersResource = keycloak.realm(realm).users()


        log.debug("usersResource obtained for realm: {}", usersResource.toString())
        // 4. Create user in Keycloak
        val response = usersResource.create(userRep)

        if (response.status == 201) {
            // 5. Obtain the generated user ID from Keycloak
            val userId = CreatedResponseUtil.getCreatedId(response)
            log.debug("Created a user in Keycloak with id: {}", userId)

            // 5.1. Assign Role to the User
            try {
                // From ROLE_ to role name in Keycloak
                val roleName = when (user.role) {
                    UserRole.ROLE_admin -> "admin"
                    UserRole.ROLE_manager -> "manager"
                    UserRole.ROLE_staff -> "staff"
                }

                val roleRepresentation = keycloak.realm(realm).roles().get(roleName).toRepresentation()
                usersResource.get(userId).roles().realmLevel().add(listOf(roleRepresentation))
                log.debug("Assigned role {} to user {}", roleName, userId)
            } catch (e: Exception) {
                log.error("Failed to assign role to user, rolling back user creation", e)
                usersResource.get(userId).remove()
                throw RuntimeException("Error on assigning the role, deleting the user", e)
            }

            try {
                // 6. Save user in local DB
                val newUser = User(
                    id = UUID.fromString(userId), // <--- This is the Keycloak user ID
                    fullName = user.username,
                    email = user.email,
                    organization = user.organization,
                    department = user.department,
                    role = user.role,
                    storageQuota = user.storageQuota,
                    createdAt = Date.from(Instant.now()),
                )
                userRepo.save(newUser)

            } catch (e: Exception) {
                // 7. Manage local DB save error
                // Rollback: delete user from Keycloak
                usersResource.get(userId).remove()
                throw RuntimeException("Error on local save, rollback keycloak", e)
            }
        } else {
            // Generic keycloak user creation error
            throw RuntimeException("Error on user creation in Keycloak: ${response.status} - ${response.statusInfo}")
        }
    }


    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun updateUser(user: UserDTO) {
        val userEntity: User = userRepo.findById(user.id).orElseThrow {
            throw UserNotFoundException("User with id ${user.id} not found")
        }

        if(userEntity.email != user.email){
            if(userRepo.getUserByEmail(user.email).isPresent)
                throw UserConflictError("Another user with that email already exists")
        }

        if (user.storageQuota != null) {
            val currentUsed = userEntity.storageUsed
            if (user.storageQuota < currentUsed) {
                throw StorageQuotaBelowUsedException(
                    "Cannot set storage quota to ${user.storageQuota} bytes: user has already used $currentUsed bytes"
                )
            }
        }

        userEntity.fullName = user.fullName
        userEntity.email = user.email
        userEntity.organization = user.organization
        userEntity.department = user.department
        userEntity.role = user.role
        userEntity.storageQuota = user.storageQuota
        userEntity.updatedAt = Date.from(Instant.now())
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun deleteUser(id: UUID) {
        // Get the authenticated user's ID from the security context
        val authenticatedUserId = getAuthenticatedUserId()

        // Check if the user is trying to delete themselves
        if (authenticatedUserId == id) {
            throw SelfDeletionNotAllowedException("Cannot delete your own user account")
        }

        val user: User = userRepo.findById(id).orElseThrow {
            UserNotFoundException("User with id $id not found")
        }

        // 1. First delete from local DB (within transaction)
        try {
            userRepo.deleteById(id)
            log.debug("Deleted user {} from local database", id)
        } catch (e: Exception) {
            log.error("Failed to delete user from local database", e)
            throw RuntimeException("Error deleting user from database", e)
        }

        // 2. Then delete from Keycloak (outside transaction scope)
        try {
            val usersResource = keycloak.realm(realm).users()
            usersResource.get(id.toString()).remove()
            log.debug("Deleted user {} from Keycloak", id)
        } catch (e: Exception) {
            // Keycloak deletion failed, rollback DB deletion
            log.error("Failed to delete user from Keycloak, rolling back database deletion", e)
            try {
                userRepo.save(user)
                log.debug("Rolled back database deletion for user {}", id)
            } catch (rollbackException: Exception) {
                log.error("CRITICAL: Failed to rollback database deletion for user {}", id, rollbackException)
                throw RuntimeException("Critical error: failed to rollback database deletion after Keycloak failure", rollbackException)
            }
            throw RuntimeException("Error deleting user from Keycloak, rolled back database deletion", e)
        }
    }
}
