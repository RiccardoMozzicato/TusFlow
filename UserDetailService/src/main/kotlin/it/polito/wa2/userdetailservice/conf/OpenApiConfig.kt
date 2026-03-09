package it.polito.wa2.userdetailservice.conf

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag

@Configuration
class OpenAPIConfig (){

    @Bean
    fun getOpenApiDocumentation(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("User Management API")
                .description("User Management Service for handling users")
                .version("0.0.1")
        ).tags(listOf(
            Tag().name("User Handling").description("API for handling file uploads")
        )).servers(listOf(
            Server().url("http://localhost:8081").description("User detail service server")
        ))
}
