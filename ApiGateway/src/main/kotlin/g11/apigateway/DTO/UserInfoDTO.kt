package g11.apigateway.DTO

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Authenticated user information returned by the API Gateway session endpoint.")
data class UserInfo(
    @Schema(description = "User UUID, matching the Keycloak subject.", example = "6f7d2e0d-8c47-4a87-96b2-317c39dbf33b")
    val uuid: String,
    @Schema(description = "Keycloak preferred username.", example = "mario.rossi")
    val username: String,
    @Schema(description = "User email address.", example = "mario.rossi@example.com")
    val email: String,
    @Schema(description = "User display name.", example = "Mario Rossi")
    val fullName: String,
    @Schema(description = "User department claim.", example = "Engineering")
    val department: String,
    @Schema(description = "User organization claim.", example = "PoliTo")
    val organization: String,
    val roles: List<String>,
    val photoProfileUrl: String? = null
    @Schema(description = "Application roles extracted from Keycloak realm roles.", example = "[\"staff\"]")
    val roles: List<String>

)
