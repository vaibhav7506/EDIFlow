package com.vaibhav.ediflow.auth;

import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.*;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;
import org.springframework.security.web.SecurityFilterChain;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

@Configuration
public class SecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain authorizationEndpoints(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        return http.build();
    }

    @Bean @Order(2)
    SecurityFilterChain applicationEndpoints(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/actuator/health").permitAll().anyRequest().authenticated())
            .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    RegisteredClientRepository registeredClients(
        @Value("${ediflow.clients.partner.id}") String partnerId,
        @Value("${ediflow.clients.partner.secret}") String partnerSecret,
        @Value("${ediflow.clients.reader.id}") String readerId,
        @Value("${ediflow.clients.reader.secret}") String readerSecret,
        @Value("${ediflow.clients.admin.id}") String adminId,
        @Value("${ediflow.clients.admin.secret}") String adminSecret) {
        RegisteredClient partner = client(partnerId, partnerSecret, Set.of("edi:write", "edi:read"));
        RegisteredClient reader = client(readerId, readerSecret, Set.of("edi:read"));
        RegisteredClient admin = client(adminId, adminSecret, Set.of("edi:write", "edi:read", "admin"));
        return new InMemoryRegisteredClientRepository(partner, reader, admin);
    }

    private RegisteredClient client(String id, String secret, Set<String> scopes) {
        var builder = RegisteredClient.withId(UUID.nameUUIDFromBytes(("ediflow-client:" + id).getBytes()).toString())
            .clientId(id).clientSecret("{noop}" + secret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS);
        scopes.forEach(builder::scope);
        return builder.build();
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer(
        @Value("${ediflow.clients.partner.id}") String partnerClientId,
        @Value("${ediflow.clients.partner.code}") String partnerCode) {
        return context -> {
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                String clientId = context.getRegisteredClient().getClientId();
                context.getClaims().claim("client_id", clientId)
                    .claim("partner_id", clientId.equals(partnerClientId) ? partnerCode : clientId);
            }
        };
    }

    @Bean JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsa = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic()).privateKey((RSAPrivateKey) keyPair.getPrivate()).keyID(UUID.randomUUID().toString()).build();
        return new ImmutableJWKSet<>(new JWKSet(rsa));
    }
    @Bean JwtDecoder jwtDecoder(JWKSource<SecurityContext> source) { return OAuth2AuthorizationServerConfiguration.jwtDecoder(source); }
    @Bean AuthorizationServerSettings authorizationServerSettings(@Value("${ediflow.issuer}") String issuer) { return AuthorizationServerSettings.builder().issuer(issuer).build(); }
    private static KeyPair generateRsaKey() { try { KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); } catch (GeneralSecurityException e) { throw new IllegalStateException(e); } }
}
