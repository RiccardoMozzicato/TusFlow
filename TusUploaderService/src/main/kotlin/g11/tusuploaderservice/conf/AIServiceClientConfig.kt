package g11.tusuploaderservice.conf

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.client.RestClient

@Configuration
class AIServiceClientConfig {

    @Bean
    @Qualifier("aiServiceClient")
    fun aiServiceClient(): RestClient {
        return RestClient.builder()
            .baseUrl("http://localhost:8083")
            .requestInterceptor { request, body, execution ->
                // Extract JWT token from SecurityContext and propagate it
                val authentication = SecurityContextHolder.getContext().authentication
                if (authentication is JwtAuthenticationToken) {
                    val token = authentication.token.tokenValue
                    request.headers.setBearerAuth(token)
                }
                execution.execute(request, body)
            }
            .build()
    }
}

