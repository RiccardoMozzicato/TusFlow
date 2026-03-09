package g11.apigateway.conf

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class RestClientConfig {
    @Bean
    @Qualifier("userServiceClient")
    fun userServiceClient(): RestClient {
        return RestClient.builder()
            .baseUrl("http://localhost:8082")
            .build()
    }
}