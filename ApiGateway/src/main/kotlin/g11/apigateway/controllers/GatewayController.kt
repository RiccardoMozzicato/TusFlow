package g11.apigateway.controllers

import g11.apigateway.DTO.UserInfo
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
class AuthController(
    @Qualifier("userServiceClient") private val userServiceClient: RestClient
) {
    private val log = LoggerFactory.getLogger(AuthController::class.java)

    @GetMapping("/auth/userinfo")
    fun getUserInfo(@AuthenticationPrincipal user: OidcUser?, csrfToken: CsrfToken): ResponseEntity<Any> {
        // 1. Se l'utente non è loggato, restituisci 401 o un oggetto anonimo
        if (user == null) {
            return ResponseEntity.status(401).build()
        }

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

        return ResponseEntity.ok(userInfo)
    }
}