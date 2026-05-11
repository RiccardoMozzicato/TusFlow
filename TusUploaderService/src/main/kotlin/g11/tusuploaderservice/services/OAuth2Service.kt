package g11.tusuploaderservice.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.Instant

@Service
class OAuth2Service(
    @Value("\${keycloak.token-uri}") private val tokenUri: String,
    @Value("\${keycloak.client-id}") private val clientId: String,
    @Value("\${keycloak.client-secret}") private val clientSecret: String
) {
    private val log = LoggerFactory.getLogger(OAuth2Service::class.java)
    private val restClient = RestClient.create()
    
    private var cachedToken: String? = null
    private var tokenExpiresAt: Instant? = null

    /**
     * Get M2M (Machine-to-Machine) access token using OAuth2 Client Credentials flow.
     * Caches the token until expiration to minimize token requests.
     */
    fun getM2MAccessToken(): String {
        // Return cached token if still valid
        if (cachedToken != null && tokenExpiresAt != null && Instant.now().isBefore(tokenExpiresAt)) {
            log.debug("Using cached M2M token (expires in {}s)", 
                Instant.now().until(tokenExpiresAt, java.time.temporal.ChronoUnit.SECONDS))
            return cachedToken!!
        }

        log.debug("Requesting new M2M access token from Keycloak")
        
        try {
            val response = restClient
                .post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("grant_type=client_credentials&client_id=$clientId&client_secret=$clientSecret")
                .retrieve()
                .body(Map::class.java) as? Map<String, Any>
                ?: throw IllegalStateException("Empty response from Keycloak token endpoint")

            val token = response["access_token"] as? String
                ?: throw IllegalStateException("No access_token in Keycloak response")
            
            val expiresIn = (response["expires_in"] as? Number)?.toLong() ?: 3600L
            
            // Cache the token with a 60-second buffer for safety
            cachedToken = token
            tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 60)
            
            log.debug("Successfully obtained M2M token, expires in {}s", expiresIn)
            return token
            
        } catch (e: Exception) {
            log.error("Failed to obtain M2M access token: {}", e.message, e)
            throw RuntimeException("Failed to obtain M2M access token", e)
        }
    }

    /**
     * Force refresh the cached token on next request.
     * Useful if a token is invalidated before expiration.
     */
    fun invalidateCache() {
        log.debug("Invalidating M2M token cache")
        cachedToken = null
        tokenExpiresAt = null
    }
}
