package com.vaibhav.ediflow.gateway;

import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class PartnerIdentityFilter implements GlobalFilter, Ordered {
    @Override public reactor.core.publisher.Mono<Void> filter(org.springframework.web.server.ServerWebExchange exchange, GatewayFilterChain chain) {
        return exchange.getPrincipal().cast(JwtAuthenticationToken.class)
            .map(auth -> exchange.mutate().request(request -> request.headers(headers -> {
                headers.remove("X-Partner-Id");
                headers.remove("X-OAuth-Client-Id");
                headers.set("X-Partner-Id", auth.getToken().getClaimAsString("partner_id"));
                headers.set("X-OAuth-Client-Id", auth.getToken().getClaimAsString("client_id"));
            })).build()).defaultIfEmpty(exchange).flatMap(chain::filter);
    }
    @Override public int getOrder() { return -1; }
}
