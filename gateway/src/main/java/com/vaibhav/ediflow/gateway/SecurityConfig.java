package com.vaibhav.ediflow.gateway;

import org.springframework.context.annotation.*;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityWebFilterChain security(ServerHttpSecurity http) {
        return http.csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(auth -> auth
                .pathMatchers("/actuator/health", "/oauth2/**", "/.well-known/**").permitAll()
                .pathMatchers("/api/v1/edi/**").hasAuthority("SCOPE_edi:write")
                .pathMatchers("/api/v1/dead-letters/**", "/api/v1/partners/**", "/api/v1/audit/**").hasAuthority("SCOPE_admin")
                .pathMatchers("/api/v1/orders/**", "/api/v1/invoices/**", "/api/v1/interchanges/**", "/api/v1/metrics/**").hasAuthority("SCOPE_edi:read")
                .anyExchange().authenticated())
            .oauth2ResourceServer(resource -> resource.jwt(jwt -> {}))
            .build();
    }
}
