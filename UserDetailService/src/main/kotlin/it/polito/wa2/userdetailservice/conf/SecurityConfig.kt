    package it.polito.wa2.userdetailservice.conf

    import org.springframework.context.annotation.Bean
    import org.springframework.context.annotation.Configuration
    import org.springframework.core.convert.converter.Converter
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
    import org.springframework.security.config.annotation.web.builders.HttpSecurity
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
    import org.springframework.security.core.GrantedAuthority
    import org.springframework.security.core.authority.SimpleGrantedAuthority
    import org.springframework.http.HttpMethod
    import org.springframework.security.oauth2.jwt.Jwt
    import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
    import org.springframework.security.web.SecurityFilterChain
    import java.util.stream.Collectors


    class KeycloakRealmRoleConverter: Converter<Jwt, MutableCollection<GrantedAuthority?>> {
        override fun convert(jwt: Jwt): MutableCollection<GrantedAuthority?>? {
            val realmAccess = jwt.getClaimAsMap("realm_access")
            if (realmAccess == null || realmAccess.isEmpty()) {
                return mutableListOf()
            }
            val roles = realmAccess["roles"] as MutableList<String?>
            return roles.stream()
                .map { role: String? -> SimpleGrantedAuthority("ROLE_"+role) }
                .collect(Collectors.toList())
        }
    }

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
        fun securityFilterChain1(httpSecurity: HttpSecurity): SecurityFilterChain {
            return httpSecurity
                .authorizeHttpRequests {
                    it.requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users", "/api/v1/users/").hasAnyRole("admin", "manager", "staff", "serviceaccount-fileservice")
                        .requestMatchers("/api/v1/users/**").hasAnyRole("admin", "serviceaccount-fileservice")
                        .anyRequest().authenticated()
                }.oauth2ResourceServer {
                    it.jwt { jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()) }
                }.build()
        }

    }
