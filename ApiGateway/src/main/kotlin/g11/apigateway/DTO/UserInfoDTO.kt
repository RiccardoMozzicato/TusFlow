package g11.apigateway.DTO

// Crea questo file, es. UserInfo.kt
data class UserInfo(
    val uuid: String,
    val username: String,
    val email: String,
    val fullName: String,
    val department: String,
    val organization: String,
    val roles: List<String>,
    val photoProfileUrl: String? = null
)