package g11.apigateway.controllers

import g11.apigateway.DTO.UserInfo
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import g11.apigateway.metrics.GatewayMetrics
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException

@RestController
@RequestMapping("/api")
@Tag(
    name = "Gateway Authentication",
    description = "Gateway-owned endpoints used by the SPA to inspect the current authenticated session.",
)
class AuthController(
    @Qualifier("userServiceClient") private val userServiceClient: RestClient,
    private val gatewayMetrics: GatewayMetrics,
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @Operation(
        summary = "Get current authenticated user",
        description = "Returns the Keycloak-backed user profile for the current gateway session. A 401 response is expected when the SPA is loaded without an authenticated session.",
        responses = [
            ApiResponse(
                responseCode = "200",
                description = "Authenticated user information.",
                content = [Content(schema = Schema(implementation = UserInfo::class))]
            ),
            ApiResponse(
                responseCode = "401",
                description = "No authenticated gateway session is available.",
                content = [Content()]
            )
        ]
    )
    @GetMapping("/auth/userinfo")
    fun getUserInfo(
        @Parameter(hidden = true) @AuthenticationPrincipal user: OidcUser?,
        @Parameter(hidden = true) csrfToken: CsrfToken,
    ): ResponseEntity<Any> {
        log.debug("Requested user info")
        // 1. Se l'utente non è loggato, restituisci 401 o un oggetto anonimo
        if (user == null) {
            // Expected: the SPA polls this endpoint on load and treats 401 as "anonymous".
            log.debug("Requested user info for a non authenticated user")
            gatewayMetrics.countAnonymous()
            return ResponseEntity.status(401).build()
        }
        gatewayMetrics.countAuthenticated()

        // Estrai i realm roles da realm_access -> roles
        val realmRoles = user.claims["realm_access"]?.let { realmAccess ->
            (realmAccess as? Map<*, *>)?.get("roles") as? List<String>
        } ?: emptyList()

        val filteredRoles = realmRoles.filter { it == "staff" || it == "admin" || it =="manager"}


        // 3. Costruzione della risposta
        val userInfo = UserInfo(
            uuid=user.name ?: "",
            username = user.preferredUsername ?: "unknown",
            email = user.email ?: "",
            fullName = user.fullName ?: "",
            department = user.claims["department"] as? String ?: "",
            organization = user.claims["organization"] as? String ?: "",
            roles = filteredRoles,
        )

        log.debug("User info for user with id={} returned", userInfo.uuid)
        return ResponseEntity.ok(userInfo)
    }
}
