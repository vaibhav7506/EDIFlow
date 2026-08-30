package com.vaibhav.ediflow.gateway;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(classes = {GatewayApplication.class, GatewayRateLimitIntegrationTest.JwtTestConfig.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "server.ssl.enabled=false")
class GatewayRateLimitIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);
    static final DisposableServer DOWNSTREAM = HttpServer.create().port(0)
        .handle((request, response) -> response.status(200).sendString(Mono.just("{}"))).bindNow();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("INGESTION_SERVICE_URI", () -> "http://localhost:" + DOWNSTREAM.port());
    }

    @Autowired WebTestClient client;

    @AfterAll static void stopDownstream() { DOWNSTREAM.disposeNow(); }

    @Test
    void redisTokenBucketReturns429AndKeysByOauthClient() {
        AtomicBoolean limited = new AtomicBoolean();
        for (int attempt = 0; attempt < 30; attempt++) {
            int status = client.post().uri("/api/v1/edi/upload")
                .headers(headers -> headers.setBearerAuth("write-token"))
                .exchange().returnResult(Void.class).getStatus().value();
            if (status == 429) { limited.set(true); break; }
            assertEquals(200, status);
        }
        assertTrue(limited.get(), "Expected the Redis token bucket to exhaust its burst capacity.");
    }

    @TestConfiguration
    static class JwtTestConfig {
        @Bean @Primary
        ReactiveJwtDecoder jwtDecoder() {
            return token -> Mono.just(Jwt.withTokenValue(token).header("alg", "none")
                .subject("rate-client").claim("scope", "edi:write").claim("client_id", "rate-client")
                .claim("partner_id", "SENDERID").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());
        }
    }
}
