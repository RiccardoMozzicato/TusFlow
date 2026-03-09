package g11.tusuploaderservice.integration

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest
@ContextConfiguration(initializers = [IntegrationTest.Initializer::class])
abstract class IntegrationTest {
    companion object {
        private val postgres = PostgreSQLContainer("postgres:15")

        private val minio = GenericContainer(DockerImageName.parse("minio/minio:latest"))
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
    }

    internal class Initializer: ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            postgres.start()
            minio.start()

            TestPropertyValues.of(
                // Database configuration
                "spring.datasource.url=${postgres.jdbcUrl}",
                "spring.datasource.username=${postgres.username}",
                "spring.datasource.password=${postgres.password}",
                "spring.datasource.driver-class-name=org.postgresql.Driver",

                // Flyway configuration for tests
                "spring.flyway.enabled=true",
                "spring.flyway.baseline-on-migrate=true",
                "spring.flyway.locations=classpath:db/migration",
                "spring.jpa.hibernate.ddl-auto=none",
                "spring.jpa.show-sql=true",

                // MinIO configuration
                "minio.url=http://${minio.host}:${minio.getMappedPort(9000)}",
                "minio.access-key=minioadmin",
                "minio.secret-key=minioadmin",
                "minio.uploads-bucket=uploads",
                "minio.files-bucket=files",

                // TUS configuration
                "tus.version=1.0.0",
                "tus.extensions=creation,creation-defer-length,creation-with-upload,expiration,checksum,termination",
                "tus.maxSize=1073741824",
                "tus.checksumAlgorithm=sha-1",
                "tus.vary=access-control-request-headers",
                "tus.allowMethods=OPTIONS,POST,HEAD,PATCH,DELETE",
                "tus.allowHeaders=*",
                "tus.exposeHeaders=location,upload-offset,upload-length,upload-expires,tus-version,tus-extension,tus-resumable,tus-checksum-algorithm,upload-metadata",
                "tus.allowOrigin=*",
                "tus.expirationPeriod=3600"
            ).applyTo(applicationContext.environment)
        }
    }
}