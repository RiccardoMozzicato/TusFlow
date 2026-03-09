package g11.aiprocessingservice.services

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestTemplate

@Service
class AuthService(
    @Value("\${keycloak.token-uri}") private val tokenUri: String,
    @Value("\${keycloak.client-id}") private val clientId: String,
    @Value("\${keycloak.client-secret}") private val clientSecret: String

) : AuthServiceInterface {
    override fun getAccessToken(): String {
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
        return response.body?.get("access_token") as String
    }
}