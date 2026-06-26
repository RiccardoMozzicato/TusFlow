package it.polito.wa2.userdetailservice.conf

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag

@Configuration
class OpenAPIConfig (){

    @Bean
    fun getOpenApiDocumentation(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("User Management API")
                .description("User Detail Service API for managing users, roles, organizations and storage quotas.")
                .version("0.0.1")
        )
        .components(
            Components().addSecuritySchemes(
                "bearerAuth",
                SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
            )
        )
        .addSecurityItem(SecurityRequirement().addList("bearerAuth"))
        .tags(listOf(
            Tag().name("User Handling").description("User profile, organization and role management endpoints.")
        ))
        .servers(listOf(
            Server().url("http://localhost:8082").description("UserDetailService direct endpoint"),
            Server().url("http://localhost:8080").description("API Gateway")
        ))
}
