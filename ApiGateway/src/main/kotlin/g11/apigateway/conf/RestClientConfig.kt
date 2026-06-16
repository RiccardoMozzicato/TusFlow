package g11.apigateway.conf

import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.web.client.RestClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
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

    /**
     * Enlarges the connection pool of the proxy HttpClient that the gateway (RestClientProxyExchange)
     * uses for every routed request.
     *
     * The default Apache HttpClient5 pool is 5 connections per route / 25 total. Each SockJS
     * HTTP-streaming session (the AI-job WebSocket channel, which the servlet gateway can only proxy
     * as a long-lived streaming response) holds one connection to the downstream for its whole
     * lifetime. With the default of 5, a handful of live channels exhaust the route's pool and every
     * other request to that service blocks until the 3-minute acquire timeout, then fails with
     * ConnectionRequestTimeoutException. Raising the limits gives ample headroom for concurrent live
     * channels plus normal API traffic.
     *
     * No socket/response timeout is set: streaming responses never "complete", so cutting them on a
     * read timeout would kill the channel. Only the pool-acquire timeout is shortened so a genuinely
     * exhausted pool fails fast instead of stalling the servlet thread for three minutes.
     *
     * Applies to the autoconfigured RestClient.Builder the gateway injects; the standalone
     * userServiceClient above builds from a fresh builder and is unaffected.
     */
    @Bean
    fun gatewayProxyRestClientCustomizer(): RestClientCustomizer {
        val connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
            .setMaxConnTotal(500)
            .setMaxConnPerRoute(200)
            .build()
        val requestConfig = RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofSeconds(10))
            .build()
        val httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build()
        return RestClientCustomizer { builder ->
            builder.requestFactory(HttpComponentsClientHttpRequestFactory(httpClient))
        }
    }
}
