package g11.aiprocessingservice.conf


import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.web.SecurityFilterChain

@EnableWebSecurity
@Configuration
@EnableMethodSecurity
class SecurityConfig {
    private fun jwtAuthenticationConverter(): JwtAuthenticationConverter {
        val converter = JwtAuthenticationConverter()
        converter.setJwtGrantedAuthoritiesConverter(KeycloakRealmRoleConverter())
        return converter
    }

    @Bean
    fun securityFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain {
        return httpSecurity
            .authorizeHttpRequests {
                    it.anyRequest().authenticated()
            }
            // Stateless Bearer-token resource server behind the gateway: every request is
            // authenticated by JWT, which is immune to CSRF, and the service is never reached
            // directly by a browser session. CSRF protection (the browser session's concern) lives
            // on the gateway. Leaving it enabled here rejects the SockJS/STOMP transport POSTs to
            // /api/v1/jobs/ws/** — sockjs-client cannot attach an X-XSRF-TOKEN header.
            .csrf { it.disable() }
            .oauth2ResourceServer {
                it.jwt { jwt-> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())}
            }
            .build()
    }
}
