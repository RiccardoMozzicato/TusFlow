package it.polito.wa2.userdetailservice.dto

import it.polito.wa2.userdetailservice.entities.User
import it.polito.wa2.userdetailservice.enums.UserRole
import java.io.InputStream
import java.util.UUID

data class  UserDTO(
    val id: UUID,
    val fullName: String,
    val email: String,
    val organization: String,
    val department: String? = null,
    val role: UserRole,
    val storageQuota: Long? = null,
    val storageUsed: Long = 0,
    val photoProfileUrl: String? = null
)

data class AvatarDTO(
    val avatar: InputStream,
    val size: Long,
    val contentType: String?
)

// Carries an avatar's bytes back from MinIO so the controller can stream them
// without ever exposing MinIO to the browser. ETag lets the client revalidate.
data class AvatarContentDTO(
    val stream: InputStream,
    val contentType: String,
    val etag: String?
)

fun User.toDTO(photoProfileUrl: String? = null): UserDTO {
    return UserDTO(
        this.id,
        this.fullName,
        this.email,
        this.organization,
        this.department,
        this.role,
        this.storageQuota,
        this.storageUsed,
        photoProfileUrl
    )
}