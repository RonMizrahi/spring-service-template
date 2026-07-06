package com.example.template.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * End-to-end test of the auth flow through the real security filter chain, JWT signing, BCrypt,
 * and H2 — the seeded demo users (admin/admin, user/user) come from DataInitialization.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private MeterRegistry meterRegistry;

    private String tokenFor(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    private String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    @Test
    @DisplayName("Login with valid credentials returns a JWT")
    void login_valid_returnsToken() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("admin", "admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    @DisplayName("Login with wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("admin", "nope")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Login with a blank username fails validation with 400")
    void login_blankUsername_returns400() throws Exception {
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(body("", "admin")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Admin endpoint accepts an admin token")
    void adminEndpoint_withAdminToken_ok() throws Exception {
        String token = tokenFor("admin", "admin");
        mockMvc.perform(get("/auth/admin-roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.roles").isArray());
    }

    @Test
    @DisplayName("Admin endpoint rejects a user token with 403")
    void adminEndpoint_withUserToken_forbidden() throws Exception {
        String token = tokenFor("user", "user");
        mockMvc.perform(get("/auth/admin-roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("User endpoint accepts a user token")
    void userEndpoint_withUserToken_ok() throws Exception {
        String token = tokenFor("user", "user");
        mockMvc.perform(get("/auth/user-roles").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"));
    }

    @Test
    @DisplayName("A protected endpoint returns 401 for a malformed token (not 500)")
    void protectedEndpoint_malformedToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v2/status").header("Authorization", "Bearer garbage.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A protected endpoint returns 401 with no token")
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v2/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A successful login increments the auth.login.attempts metric via the aspect")
    void successfulLogin_incrementsLoginMetric() throws Exception {
        double before = meterRegistry.get("auth.login.attempts").counter().count();
        tokenFor("admin", "admin");
        double after = meterRegistry.get("auth.login.attempts").counter().count();
        assertThat(after).isGreaterThan(before);
    }
}
