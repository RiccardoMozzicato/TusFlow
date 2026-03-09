package g11.apigateway

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.invoke
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler
import org.springframework.security.web.csrf.CsrfTokenRequestHandler
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler
import org.springframework.util.StringUtils
import java.util.function.Supplier

//Providing beans for security configuration
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
class SecurityConfig ( val crr : ClientRegistrationRepository){
    private val log = LoggerFactory.getLogger(SecurityConfig::class.java)
    @Value("\${baseUrl}") private lateinit var baseUrl: String

    fun oidcLogoutSuccessHandler() = OidcClientInitiatedLogoutSuccessHandler(crr)
        .also{ it.setPostLogoutRedirectUri("$baseUrl/ui?logout")}


    @Bean
    fun securityFilterChain(httpSecurity: HttpSecurity): SecurityFilterChain{

        httpSecurity.
                authorizeHttpRequests{
                        it.requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/api/auth/userinfo").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/ui/**", "/ui").permitAll()
                        .requestMatchers("/oauth2/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/logout").permitAll()
                        .requestMatchers("/api/v1/outbox/**").permitAll()
                        .anyRequest().authenticated()
                }
                .oauth2Login { oauth2 ->
                        oauth2.defaultSuccessUrl("/ui", true)
                }
            .csrf { it.ignoringRequestMatchers("/api/v1/outbox/**") }
                .logout { it.logoutSuccessHandler(oidcLogoutSuccessHandler()) }

        httpSecurity{
            csrf {
                csrfTokenRepository = CookieCsrfTokenRepository.withHttpOnlyFalse()
                csrfTokenRequestHandler = SpaCsrfTokenRequestHandler()
            }
        }
        return httpSecurity.build()
    }
}

class SpaCsrfTokenRequestHandler : CsrfTokenRequestHandler {
    private val plain: CsrfTokenRequestHandler = CsrfTokenRequestAttributeHandler()
    private val xor: CsrfTokenRequestHandler = XorCsrfTokenRequestAttributeHandler()

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        csrfToken: Supplier<CsrfToken>?
    ) {
        /*
         * Always use XorCsrfTokenRequestAttributeHandler to provide BREACH protection of
         * the CsrfToken when it is rendered in the response body.
         */
        xor.handle(request, response, csrfToken)
        /*
         * Render the token value to a cookie by causing the deferred token to be loaded.
         */
        csrfToken?.get()
    }

    override fun resolveCsrfTokenValue(request: HttpServletRequest, csrfToken: CsrfToken): String? {
        val headerValue = request.getHeader(csrfToken.headerName)
        /*
         * If the request contains a request header, use CsrfTokenRequestAttributeHandler
         * to resolve the CsrfToken. This applies when a single-page application includes
         * the header value automatically, which was obtained via a cookie containing the
         * raw CsrfToken.
         */
        return if (StringUtils.hasText(headerValue)) {
            plain
        } else {
            /*
             * In all other cases (e.g. if the request contains a request parameter), use
             * XorCsrfTokenRequestAttributeHandler to resolve the CsrfToken. This applies
             * when a server-side rendered form includes the _csrf request parameter as a
             * hidden input.
             */
            xor
        }.resolveCsrfTokenValue(request, csrfToken)
    }
}