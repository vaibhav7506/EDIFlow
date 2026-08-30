package com.vaibhav.ediflow.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static org.mockito.Mockito.*;

class RateLimitIdentityTest {
    @Test void rateLimitKeyComesFromOauthClientId() {
        var config = new GatewayConfig();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("partner-client").claim("client_id", "partner-client").build();
        ServerWebExchange exchange = mock(ServerWebExchange.class);
        when(exchange.getPrincipal()).thenReturn(Mono.just(new JwtAuthenticationToken(jwt)));
        StepVerifier.create(config.clientIdKeyResolver().resolve(exchange)).expectNext("partner-client").verifyComplete();
    }
}
