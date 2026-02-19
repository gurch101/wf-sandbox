package com.gurch.sandbox.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OAuthClientCredentialsIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void shouldIssueClientCredentialsTokenAndResolveSystemUserSubject() throws Exception {
    UUID systemUserId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, true, now(), now())
        """,
        Map.of(
            "id", systemUserId,
            "username", "sys-client-a",
            "email", "sys-client-a@local.invalid"));
    jdbcTemplate.update(
        """
        INSERT INTO oauth_clients (client_id, client_secret_hash, grant_types, scopes, enabled)
        VALUES (:clientId, :secret, :grantTypes, :scopes, true)
        """,
        Map.of(
            "clientId", "machine-client-a",
            "secret", "{noop}machine-secret-a",
            "grantTypes", "client_credentials",
            "scopes", "api.read api.write"));
    jdbcTemplate.update(
        """
        INSERT INTO system_client_users (client_id, user_id)
        VALUES (:clientId, :userId)
        """,
        Map.of("clientId", "machine-client-a", "userId", systemUserId));

    MvcResult tokenResult =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", basic("machine-client-a", "machine-secret-a"))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("grant_type=client_credentials&scope=api.read"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andReturn();

    JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
    String accessToken = tokenJson.get("access_token").asText();

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(systemUserId.toString()))
        .andExpect(jsonPath("$.username").value(systemUserId.toString()));
  }

  private String basic(String username, String password) {
    String raw = username + ":" + password;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
  }
}
