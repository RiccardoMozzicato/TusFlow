package it.polito.wa2.userdetailservice

import dasniko.testcontainers.keycloak.KeycloakContainer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTest.Initializer::class])
abstract class IntegrationTest {
    companion object {
        private val db = PostgreSQLContainer<Nothing>("postgres:15")

        val keycloakContainer: KeycloakContainer = KeycloakContainer("quay.io/keycloak/keycloak:26.4")
            .withRealmImportFile("keycloak/test-realm.json")
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
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=$keycloakBaseUrl/realms/file-management/protocol/openid-connect/certs",
            ).applyTo(applicationContext.environment)
        }
    }
}