package g11.tusuploaderservice.conf

import g11.tusuploaderservice.services.OAuth2Service
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

    @Bean
    @Qualifier("aiProcessingServiceClient")
    fun aiProcessingServiceClient(): RestClient {
        return RestClient.builder()
            .baseUrl("http://localhost:8081")
            .build()
    }

    @Bean
    @Qualifier("aiProcessingServiceM2MClient")
    fun aiProcessingServiceM2MClient(oAuth2Service: OAuth2Service): RestClient {
        return RestClient.builder()
            .baseUrl("http://localhost:8081")
            .requestInterceptor { request, body, execution ->
                // Get M2M access token and add it to the request
                val token = oAuth2Service.getM2MAccessToken()
                request.headers.setBearerAuth(token)
                execution.execute(request, body)
            }
            .build()
    }
}