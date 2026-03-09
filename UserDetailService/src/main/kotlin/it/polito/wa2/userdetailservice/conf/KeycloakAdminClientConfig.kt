package it.polito.wa2.userdetailservice.conf

import org.keycloak.OAuth2Constants
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KeycloakAdminConfig(
    @Value("\${keycloak-admin.realm}")
    private val realm: String,
    @Value("\${keycloak-admin.client-id}")
    private val clientId: String,
    @Value("\${keycloak-admin.secret}")
    private val clientSecret: String,
    @Value("\${keycloak-admin.server-url}")
    private val serverUrl: String
) {

    @Bean
    fun keycloakAdminClient(): Keycloak {
        return KeycloakBuilder.builder()
            .serverUrl(serverUrl)
            .realm(realm)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .grantType(OAuth2Constants.CLIENT_CREDENTIALS) // <--- Questo è il trucco
            .build()
    }
}