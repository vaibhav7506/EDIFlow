package com.vaibhav.ediflow.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class TokenEndpointTest {
    @Autowired MockMvc mvc;
    @Test void clientCredentialsIssuesScopedAccessToken() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("partner-client", "change-me-partner"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials").param("scope", "edi:read edi:write"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.access_token").isNotEmpty())
            .andExpect(jsonPath("$.scope").value(org.hamcrest.Matchers.containsString("edi:write")));
    }
    @Test void invalidSecretIsRejected() throws Exception {
        mvc.perform(post("/oauth2/token").with(httpBasic("partner-client", "wrong"))
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("grant_type", "client_credentials"))
            .andExpect(status().isUnauthorized());
    }
}
