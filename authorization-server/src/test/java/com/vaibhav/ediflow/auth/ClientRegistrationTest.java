package com.vaibhav.ediflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ClientRegistrationTest {
    @Autowired RegisteredClientRepository clients;
    @Test void clientsHaveExpectedScopes() {
        assertEquals(java.util.Set.of("edi:read", "edi:write"), clients.findByClientId("partner-client").getScopes());
        assertEquals(java.util.Set.of("edi:read"), clients.findByClientId("reader-client").getScopes());
        assertTrue(clients.findByClientId("admin-client").getScopes().contains("admin"));
    }
}
