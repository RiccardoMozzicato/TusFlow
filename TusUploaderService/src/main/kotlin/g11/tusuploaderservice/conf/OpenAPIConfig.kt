package g11.tusuploaderservice.conf

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
                .title("File management system API")
                .description("TusUploaderService API for resumable TUS uploads, file metadata, sharing, downloads, versioning and favorites.")
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
            Tag().name("Tus Server Capabilities").description("TUS protocol discovery and server capability endpoints."),
            Tag().name("File Handling").description("TUS upload creation, status, chunk transfer and termination endpoints."),
            Tag().name("File Management").description("File listing, metadata, download, preview and AI-generated metadata endpoints."),
            Tag().name("File Sharing").description("Endpoints to share files with users, update permissions and list shared files."),
            Tag().name("File Versioning").description("Endpoints to create and inspect file versions."),
            Tag().name("Version Favorites").description("Endpoints to add, remove and list favorite file versions.")
        ))
        .servers(listOf(
            Server().url("http://localhost:8081").description("TusUploaderService direct server"),
            Server().url("http://localhost:8080").description("API Gateway")
        ))
}
