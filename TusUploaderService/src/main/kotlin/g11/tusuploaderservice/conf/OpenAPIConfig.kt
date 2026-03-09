package g11.tusuploaderservice.conf

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import io.swagger.v3.oas.models.tags.Tag
import org.springframework.beans.factory.annotation.Value

@Configuration
class OpenAPIConfig (){

    @Bean
    fun getOpenApiDocumentation(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("File management system API")
                .description("File management system with TUS protocol support implemented by group 11")
                .version("0.0.1")
        ).tags(listOf(
            Tag().name("File Handling").description("API for handling file uploads"),
            Tag().name("Tus Server Capabilities").description("API for retrieving Tus Server capabilities"),
            Tag().name("File Management").description("Endpoints for deleting and downloading files or listing their metadata and update them"),
            Tag().name("File Sharing").description("Endpoints to share files with users and list shares"),
            Tag().name("File Versioning").description("Endpoints to create and manage file versions")
        )).servers(listOf(
            Server().url("http://localhost:8080").description("TUS Server")
        ))
}
