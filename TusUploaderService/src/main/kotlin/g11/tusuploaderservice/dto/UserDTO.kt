package g11.tusuploaderservice.dto


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

enum class UserRole {
    ROLE_admin,
    ROLE_manager,
    ROLE_staff
}