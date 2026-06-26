package g11.apigateway.conf

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun apiGatewayOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("DocPortal API Gateway")
                .description(
                    """
                    Public entry point for the DocPortal APIs.

                    The gateway owns the browser authentication endpoints and forwards the public API
                    surface to the backend services with OAuth2 TokenRelay:

                    - `/api/v1/files/**`, `/api/v1/uploads/**`, `/api/v1/outbox/**` -> TusUploaderService
                    - `/api/v1/users/**` -> UserDetailService
                    - `/api/v1/jobs/**` -> AIProcessingService job status and retry APIs
                    - `/api/rag/**` -> AIProcessingService RAG APIs
                    - `/ui/**` -> frontend application

                    Swagger UI also links the TusUploaderService, UserDetailService and
                    AIProcessingService OpenAPI documents through gateway-local proxy routes.
                    """.trimIndent()
                )
                .version("0.0.1")
        )
        .components(
            Components().addSecuritySchemes(
                "oauth2Login",
                SecurityScheme()
                    .type(SecurityScheme.Type.OPENIDCONNECT)
                    .openIdConnectUrl("http://localhost:9090/realms/file-management/.well-known/openid-configuration")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("oauth2Login"))
        .tags(
            listOf(
                Tag()
                    .name("Gateway Authentication")
                    .description("Gateway-owned session and authenticated user endpoints."),
                Tag()
                    .name("Public Gateway Routes")
                    .description("Routes exposed through the gateway and proxied to backend services."),
            )
        )
        .servers(
            listOf(
                Server().url("http://localhost:8080").description("API Gateway"),
            )
        )
}
