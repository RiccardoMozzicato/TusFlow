package g11.tusuploaderservice.services

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@Service
class KeycloakAuthService(
    @Value("\${keycloak.token-uri}") private val tokenUri: String,
    @Value("\${keycloak.client-id}") private val clientId: String,
    @Value("\${keycloak.client-secret}") private val clientSecret: String
) : AuthService {

    private val log = LoggerFactory.getLogger(KeycloakAuthService::class.java)

    override fun getAccessToken(): String {
        log.debug("Requesting access token from Keycloak")

        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_FORM_URLENCODED
        }
        val body = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "client_credentials")
            add("client_id", clientId)
            add("client_secret", clientSecret)
        }
        val restTemplate = RestTemplate()
        val request = HttpEntity(body, headers)
        val response = restTemplate.postForEntity(tokenUri, request, Map::class.java)
        log.debug("Received access token from Keycloak")
        return response.body?.get("access_token") as String
    }
}


