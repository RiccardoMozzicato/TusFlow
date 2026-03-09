package it.polito.wa2.userdetailservice.dto

import it.polito.wa2.userdetailservice.enums.UserRole

data class UserCreationDTO(
    val username: String,
    val email: String,
    val organization: String,
    val role: UserRole,
    val department: String?,
    val storageQuota: Long?
)
