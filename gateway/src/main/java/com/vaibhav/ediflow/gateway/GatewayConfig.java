package com.vaibhav.ediflow.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.*;
import org.springframework.cloud.gateway.route.*;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

@Configuration
public class GatewayConfig {
    @Bean RedisRateLimiter redisRateLimiter() { return new RedisRateLimiter(5, 10, 1); }

    @Bean KeyResolver clientIdKeyResolver() {
        return exchange -> exchange.getPrincipal().cast(JwtAuthenticationToken.class)
            .map(authentication -> authentication.getToken().getClaimAsString("client_id"))
            .switchIfEmpty(Mono.just("anonymous"));
    }

    @Bean
    RouteLocator routes(RouteLocatorBuilder builder, RedisRateLimiter rateLimiter, KeyResolver keyResolver,
        @Value("${AUTH_SERVER_URI:http://localhost:9000}") String authServer,
        @Value("${INGESTION_SERVICE_URI:http://localhost:8081}") String ingestionService,
        @Value("${ORDER_SERVICE_URI:http://localhost:8082}") String orderService) {
        return builder.routes()
            .route("authorization-server", route -> route.path("/oauth2/**", "/.well-known/**")
                .uri(authServer))
            .route("edi-status", route -> route.path("/api/v1/edi/status/**")
                .filters(filters -> filters.requestRateLimiter(config -> config.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                .uri(orderService))
            .route("ingestion-service", route -> route.path("/api/v1/edi/**")
                .filters(filters -> filters.requestRateLimiter(config -> config.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                .uri(ingestionService))
            .route("order-service", route -> route.path("/api/v1/**")
                .filters(filters -> filters.requestRateLimiter(config -> config.setRateLimiter(rateLimiter).setKeyResolver(keyResolver)))
                .uri(orderService))
            .build();
    }
}
