package com.vaibhav.ediflow.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewaySecurityTest {
    @Autowired WebTestClient client;
    @MockBean ReactiveJwtDecoder decoder;
    @BeforeEach void jwt() {
        Jwt readJwt = Jwt.withTokenValue("read-token").header("alg", "none").subject("reader-client")
            .claim("scope", "edi:read").claim("client_id", "reader-client").claim("partner_id", "reader-client")
            .issuedAt(java.time.Instant.now()).expiresAt(java.time.Instant.now().plusSeconds(300)).build();
        when(decoder.decode("read-token")).thenReturn(Mono.just(readJwt));
        when(decoder.decode("invalid-token")).thenReturn(Mono.error(new org.springframework.security.oauth2.jwt.BadJwtException("invalid")));
    }
    @Test void uploadWithoutTokenIsUnauthorized(){client.post().uri("/api/v1/edi/upload").exchange().expectStatus().isUnauthorized();}
    @Test void readOnlyTokenCannotUpload(){client.post().uri("/api/v1/edi/upload").headers(h->h.setBearerAuth("read-token")).exchange().expectStatus().isForbidden();}
    @Test void nonAdminCannotRetryDeadLetter(){client.post().uri("/api/v1/dead-letters/00000000-0000-0000-0000-000000000000/retry").headers(h->h.setBearerAuth("read-token")).exchange().expectStatus().isForbidden();}
    @Test void invalidJwtIsUnauthorized(){client.get().uri("/api/v1/orders").headers(h->h.setBearerAuth("invalid-token")).exchange().expectStatus().isUnauthorized();}
}
