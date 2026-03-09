package it.polito.wa2.userdetailservice.dto

import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import java.util.UUID

data class  UserDTO(
    val id: UUID,
    val fullName: String,
    val email: String,
    val organization: String,
    val department: String? = null,
    val role: UserRole,
    val storageQuota: Long? = null
)

fun User.toDTO(): UserDTO {
    return UserDTO(
        this.id,
        this.fullName,
        this.email,
        this.organization,
        this.department,
        this.role,
        this.storageQuota
    )
}