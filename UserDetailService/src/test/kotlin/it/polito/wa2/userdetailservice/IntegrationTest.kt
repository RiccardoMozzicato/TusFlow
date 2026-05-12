package it.polito.wa2.userdetailservice

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer

private const val REALM_FILE = "keycloak/file-management-realm.json"

data class RealmUser(val username: String, val password: String)

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTest.Initializer::class])
abstract class IntegrationTest {
    companion object {
        private val db = PostgreSQLContainer<Nothing>("postgres:15")

        val keycloakContainer: KeycloakContainer = KeycloakContainer("quay.io/keycloak/keycloak:26.4")
            .withRealmImportFile(REALM_FILE)

        val realmName: String
        val gatewayClientId: String
        val gatewayClientSecret: String
        val usersByRole: Map<String, RealmUser>

        init {
            val json: JsonNode = ObjectMapper().readTree(
                IntegrationTest::class.java.classLoader.getResourceAsStream(REALM_FILE)
            )

            realmName = json["realm"].asText()

            val client = json["clients"].first { it["clientId"].asText() == "gateway-client" }
            gatewayClientId = client["clientId"].asText()
            gatewayClientSecret = client["secret"].asText()

            usersByRole = json["users"].flatMap { user ->
                val username = user["username"].asText()
                val password = user["credentials"].first()["value"].asText()
                val roles = user["realmRoles"].map { it.asText() }
                roles.map { role -> role to RealmUser(username, password) }
            }.toMap()
        }
    }

    internal class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            db.start()
            keycloakContainer.start()

            val keycloakBaseUrl = keycloakContainer.authServerUrl.trimEnd('/')

            TestPropertyValues.of(
                "spring.datasource.url=${db.jdbcUrl}",
                "spring.datasource.username=${db.username}",
                "spring.datasource.password=${db.password}",
                "spring.flyway.baseline-on-migrate=true",
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=$keycloakBaseUrl/realms/$realmName/protocol/openid-connect/certs",
                "spring.security.oauth2.resourceserver.jwt.issuer-uri=$keycloakBaseUrl/realms/$realmName",
                "spring.security.oauth2.client.provider.keycloak.issuer-uri=$keycloakBaseUrl/realms/$realmName",
            ).applyTo(applicationContext.environment)
        }
    }
}
