package g11.aiprocessingservice.conf

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
    fun aiProcessingOpenApi(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("AI Processing Service API")
                .description(
                    """
                    AIProcessingService API for AI job status, retry management and document RAG.

                    The file-analysis and embedding pipelines are event-driven: TusUploaderService
                    publishes file events to Kafka, this service analyzes supported files, stores job
                    state, writes AI metadata outbox events, and indexes text chunks into pgvector.

                    Realtime delivery uses STOMP over SockJS at `/api/v1/jobs/ws`:

                    - subscribe to `/user/queue/jobs` for terminal AI job updates
                    - subscribe to `/user/queue/rag` for streamed RAG answer frames

                    REST clients normally access these endpoints through the API Gateway.
                    """.trimIndent()
                )
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
        .tags(
            listOf(
                Tag()
                    .name("AI Job Status")
                    .description("Dashboard and document-library endpoints for reading AI job state."),
                Tag()
                    .name("AI Job Retry")
                    .description("Manual retry endpoint for failed file-analysis jobs."),
                Tag()
                    .name("RAG")
                    .description("Retrieval-augmented generation endpoints over the user's accessible documents."),
            )
        )
        .servers(
            listOf(
                Server().url("http://localhost:8083").description("AIProcessingService direct endpoint"),
                Server().url("http://localhost:8080").description("API Gateway"),
            )
        )
}
