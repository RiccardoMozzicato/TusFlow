package it.polito.wa2.userdetailservice.services

import it.polito.wa2.userdetailservice.dto.AvatarContentDTO
import it.polito.wa2.userdetailservice.dto.AvatarDTO
import it.polito.wa2.userdetailservice.dto.FiltersDTO
import it.polito.wa2.userdetailservice.dto.UserCreationDTO
import it.polito.wa2.userdetailservice.dto.UserDTO
import it.polito.wa2.userdetailservice.dto.toDTO
import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import it.polito.wa2.userdetailservice.exceptions.AvatarTypeUnsupported
import it.polito.wa2.userdetailservice.exceptions.SelfDeletionNotAllowedException
import it.polito.wa2.userdetailservice.exceptions.StorageQuotaBelowUsedException
import it.polito.wa2.userdetailservice.exceptions.UserConflictError
import it.polito.wa2.userdetailservice.exceptions.UserNotFoundException
import it.polito.wa2.userdetailservice.repositories.UserRepository
import it.polito.wa2.userdetailservice.specs.UserSpecifications
import jakarta.transaction.Transactional
import org.jboss.resteasy.plugins.providers.InputStreamProvider
import org.keycloak.admin.client.CreatedResponseUtil
import org.keycloak.admin.client.Keycloak
import org.keycloak.representations.idm.CredentialRepresentation
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service
import java.io.InputStream
import java.time.Instant
import java.util.Date
import java.util.UUID

@Service
class UserService(
    private var userRepo: UserRepository,
    private val minioService: minioServiceInterface,
    @Value("\${keycloak-admin.realm}") private val realm: String,
    private val keycloak: Keycloak, // Il tuo bean configurato
): UserServiceInterface {

    val log = LoggerFactory.getLogger(UserService::class.java)

    companion object {
        private const val DEFAULT_SORT_FIELD = "fullName"
        // Direct User columns that may be sorted on; guards against arbitrary
        // property names reaching the JPA sort and throwing.
        private val SORTABLE_FIELDS = setOf(
            "fullName", "email", "department", "role", "storageUsed", "createdAt",
        )
    }

    private fun getAuthenticatedUserId(): UUID {
        val authentication = SecurityContextHolder.getContext().authentication
        val jwt = authentication.principal as Jwt
        return UUID.fromString(jwt.subject)
    }

    // Gateway-relative path the frontend uses as an <img src>. The browser hits the
    // gateway same-origin (which proxies to this service); MinIO is never exposed.
    // Null when the user has no stored avatar, so the UI falls back to initials.
    // The ?v= token (the user's updatedAt) changes on every upload so the browser
    // refetches the new image immediately instead of showing the cached one — the
    // object key is stable, so without it the <img src> would never change.
    private fun User.avatarPath(): String? =
        imageObjectKey?.let { "/api/v1/users/$id/avatar?v=${updatedAt?.time ?: 0}" }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUsers(filtersDTO: FiltersDTO): Page<UserDTO> {
        // Only direct, sortable columns are accepted; anything else (or null) falls
        // back to fullName so a bad `sort` param can't 500 with an invalid property.
        val sortField = filtersDTO.sortField?.takeIf { it in SORTABLE_FIELDS } ?: DEFAULT_SORT_FIELD
        val pageable = PageRequest.of(
            filtersDTO.pageNumber,
            filtersDTO.pageSize,
            Sort.by(filtersDTO.sortDirection, sortField),
        )

        // Service accounts have no row in userRepo, so they list every user (no org scope).
        val isServiceAccount = SecurityContextHolder.getContext().authentication.authorities
            .any { it.authority == "ROLE_serviceaccount-fileservice" }

        val organization = if (isServiceAccount) {
            null
        } else {
            val authenticatedUserId = getAuthenticatedUserId()
            val authenticatedUser = userRepo.findById(authenticatedUserId).orElseThrow {
                log.warn("authenticated user {} not found", authenticatedUserId)
                UserNotFoundException("The user with id $authenticatedUserId not found")
            }
            authenticatedUser.organization
        }

        log.debug(
            "returning users page: page={}, size={}, search={}, sort={},{}, orgScoped={}",
            filtersDTO.pageNumber, filtersDTO.pageSize, filtersDTO.search, sortField, filtersDTO.sortDirection, organization != null,
        )
        val spec = UserSpecifications.buildUsers(organization, filtersDTO.search)
        return userRepo.findAll(spec, pageable).map { it.toDTO(it.avatarPath()) }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUser(id: UUID): UserDTO {
        val user: User = userRepo.findById(id).orElseThrow{
            log.warn("user {} not found", id)
            UserNotFoundException("The user with id $id not found")
        }

        log.debug("returning user {}", id)
        return user.toDTO(user.avatarPath())
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin','ROLE_manager','ROLE_staff', 'ROLE_serviceaccount-fileservice')")
    override fun getUsersBatch(ids: List<UUID>): Map<UUID, UserDTO> {
        log.debug("Requested batch of {} users", ids.size)
        val users = userRepo.findAllById(ids)
        log.debug("Returning batch of {} users", users.size)
        return users.associate { it.id to it.toDTO() }
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun createUser(user: UserCreationDTO) {
        log.debug("Creating user username={}, email={}, org={}, role={}", user.username, user.email, user.organization, user.role)

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

        // 4. Create user in Keycloak
        log.info("Creating user in Keycloak with username={}", user.username)
        val response = usersResource.create(userRep)

        if (response.status == 201) {
            // 5. Obtain the generated user ID from Keycloak
            val userId = CreatedResponseUtil.getCreatedId(response)
            log.info("User created in Keycloak userId={}", userId)

            // 5.1. Assign Role to the User
            try {
                // From ROLE_ to role name in Keycloak
                val roleName = when (user.role) {
                    UserRole.ROLE_admin -> "admin"
                    UserRole.ROLE_manager -> "manager"
                    UserRole.ROLE_staff -> "staff"
                }

                log.debug("Assigning role {} to user {}", roleName, userId)
                val roleRepresentation = keycloak.realm(realm).roles().get(roleName).toRepresentation()
                usersResource.get(userId).roles().realmLevel().add(listOf(roleRepresentation))
                log.debug("Assigned role {} to user {}", roleName, userId)
            } catch (e: Exception) {
                log.error("Role assignment failed for userId={}, rolling back Keycloak user", userId, e)
                usersResource.get(userId).remove()
                throw RuntimeException("Error on assigning the role, deleting the user", e)
            }

            try {
                // 6. Save user in local DB
                log.info("Saving user in local DB userId={}", userId)

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
                log.debug("Saving the user {} in local DB", newUser.id)
                userRepo.save(newUser)
                log.info("User persisted in local DB userId={}", userId)
            } catch (e: Exception) {
                // 7. Manage local DB save error
                // Rollback: delete user from Keycloak
                log.error("Unable to save the user {}", userId, e)
                usersResource.get(userId).remove()
                throw RuntimeException("Error on local save, rollback keycloak", e)
            }
        } else {
            // Generic keycloak user creation error
            log.error("Failed to create user in Keycloak username={} status={}", user.username, response.status)
            throw RuntimeException("Error on user creation in Keycloak: ${response.status} - ${response.statusInfo}")
        }
    }


    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun updateUser(user: UserDTO) {
        log.debug("Updating user id={}", user.id)

        val userEntity: User = userRepo.findById(user.id).orElseThrow {
            log.warn("Update failed: user not found id={}", user.id)
            throw UserNotFoundException("User with id ${user.id} not found")
        }

        if(userEntity.email != user.email){
            if(userRepo.getUserByEmail(user.email).isPresent){
                log.warn("Update rejected due to email conflict userId={} email={}", user.id, user.email)
                throw UserConflictError("Another user with that email already exists")
            }
        }

        if (user.storageQuota != null) {
            val currentUsed = userEntity.storageUsed
            if (user.storageQuota < currentUsed) {
                log.warn("Update rejected due to quota lower than used userId={} quota={} used={}", user.id, user.storageQuota, currentUsed)
                throw StorageQuotaBelowUsedException(
                    "Cannot set storage quota to ${user.storageQuota} bytes: user has already used $currentUsed bytes"
                )
            }
        }

        log.debug("Applying updates to user id={}", user.id)
        userEntity.fullName = user.fullName
        userEntity.email = user.email
        userEntity.organization = user.organization
        userEntity.department = user.department
        userEntity.role = user.role
        userEntity.storageQuota = user.storageQuota
        userEntity.updatedAt = Date.from(Instant.now())

        try {
            val userResource = keycloak.realm(realm).users().get(user.id.toString())
            val userRepresentation = userResource.toRepresentation()
            val fullNameParts = user.fullName.trim().split(" ", limit = 2)
            userRepresentation.firstName = fullNameParts.getOrNull(0) ?: ""
            userRepresentation.lastName = fullNameParts.getOrNull(1) ?: ""
            userRepresentation.email = user.email
            userResource.update(userRepresentation)
        } catch(e: Exception) {
            throw RuntimeException("Error updating user", e)
        }
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun updateAvatar(userId: String, avatar: AvatarDTO) {

        if (avatar.contentType == null || !avatar.contentType.startsWith("image/")) {
            throw AvatarTypeUnsupported()
        }

        val user = userRepo.findById(UUID.fromString(userId)).orElseThrow {
            log.warn("User with id {} not found", userId)
            UserNotFoundException("user not found")
        }

        val objectKey = "${userId}/avatar"
        minioService.putAvatar(objectKey, avatar)
        user.imageObjectKey = objectKey
        // Bump updatedAt so avatarPath()'s ?v= token changes and clients refetch.
        user.updatedAt = Date.from(Instant.now())
    }

    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun getAvatarContent(userId: UUID): AvatarContentDTO? {
        val user = userRepo.findById(userId).orElseThrow {
            log.warn("User with id {} not found", userId)
            UserNotFoundException("user not found")
        }

        val objectKey = user.imageObjectKey ?: return null
        return minioService.getAvatar(objectKey)
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ROLE_admin', 'ROLE_manager', 'ROLE_staff')")
    override fun deleteAvatar(userId: String) {
        val user = userRepo.findById(UUID.fromString(userId)).orElseThrow {
            log.warn("User with id {} not found", userId)
            UserNotFoundException("user not found")
        }

        user.imageObjectKey?.let { minioService.removeAvatar(it) }
        user.imageObjectKey = null
    }

    @Transactional
    @PreAuthorize("hasRole('ROLE_admin')")
    override fun deleteUser(id: UUID) {
        log.debug("Deleting user {}", id)
        // Get the authenticated user's ID from the security context
        val authenticatedUserId = getAuthenticatedUserId()

        // Check if the user is trying to delete themselves
        if (authenticatedUserId == id) {
            log.warn("Self-deletion attempt blocked userId={}", id)
            throw SelfDeletionNotAllowedException("Cannot delete your own user account")
        }

        val user: User = userRepo.findById(id).orElseThrow {
            log.warn("Delete failed: user {} not found", id)
            UserNotFoundException("User with id $id not found")
        }

        // 1. First delete from local DB (within transaction)
        try {
            log.debug("Deleting user from local database userId={}", id)
            userRepo.deleteById(id)
            log.info("User deleted from local database userId={}", id)
        } catch (e: Exception) {
            log.error("Failed to delete user from local database userId={}", id, e)
            throw RuntimeException("Error deleting user from database", e)
        }

        // 2. Then delete from Keycloak (outside transaction scope)
        try {
            log.debug("Deleting user from Keycloak userId={}", id)
            val usersResource = keycloak.realm(realm).users()
            usersResource.get(id.toString()).remove()
            log.info("User deleted from Keycloak userId={}", id)
        } catch (e: Exception) {
            // Keycloak deletion failed, rollback DB deletion
            log.error("Keycloak deletion failed for userId={}, rolling back DB deletion", id, e)
            try {
                userRepo.save(user)
                log.warn("Rollback succeeded: restored deleted userId={}", id)
            } catch (rollbackException: Exception) {
                log.error("CRITICAL: rollback failed userId={}", id, rollbackException)
                throw RuntimeException("Critical error: failed to rollback database deletion after Keycloak failure", rollbackException)
            }
            throw RuntimeException("Error deleting user from Keycloak, rolled back database deletion", e)
        }
    }
}
