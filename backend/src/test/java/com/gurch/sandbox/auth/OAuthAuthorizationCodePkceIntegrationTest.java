package com.gurch.sandbox.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class OAuthAuthorizationCodePkceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private RegisteredClientRepository registeredClientRepository;

  @Test
  void shouldIssueAuthorizationCodePkceTokenWithUserUuidSubject() throws Exception {
    UUID userId = UUID.fromString("33333333-3333-3333-3333-333333333333");
    String password = "Pa55w0rd!";
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", userId, "username", "alice", "email", "alice@local.invalid"));
    jdbcTemplate.update(
        """
        INSERT INTO user_credentials (user_id, password_hash, password_updated_at)
        VALUES (:userId, :passwordHash, now())
        """,
        Map.of("userId", userId, "passwordHash", "{noop}" + password));
    jdbcTemplate.update(
        """
        INSERT INTO oauth_clients (client_id, client_secret_hash, grant_types, scopes, redirect_uris, enabled)
        VALUES (:clientId, :secret, :grantTypes, :scopes, :redirectUris, true)
        """,
        Map.of(
            "clientId", "react-spa-client",
            "secret", "",
            "grantTypes", "authorization_code refresh_token",
            "scopes", "api.read",
            "redirectUris", "http://127.0.0.1:3000/callback"));

    assertThat(registeredClientRepository.findByClientId("react-spa-client")).isNotNull();
    assertThat(
            registeredClientRepository
                .findByClientId("react-spa-client")
                .getAuthorizationGrantTypes())
        .anyMatch(grantType -> "authorization_code".equals(grantType.getValue()));

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/login")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content("username=alice@local.invalid&password=" + password))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);
    assertThat(session).isNotNull();

    String verifier = "my-pkce-verifier-1234567890";
    String challenge = pkceS256(verifier);
    String state = "state-123";

    MvcResult authorizeResult =
        mockMvc
            .perform(
                get("/oauth2/authorize?response_type=code"
                        + "&client_id=react-spa-client"
                        + "&redirect_uri=http://127.0.0.1:3000/callback"
                        + "&scope=api.read"
                        + "&code_challenge="
                        + challenge
                        + "&code_challenge_method=S256"
                        + "&state="
                        + state)
                    .session(session))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", org.hamcrest.Matchers.containsString("code=")))
            .andReturn();

    String location = authorizeResult.getResponse().getHeader("Location");
    URI redirectUri = URI.create(location);
    String code = extractQueryParam(redirectUri.getRawQuery(), "code");

    MvcResult tokenResult =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(
                        "grant_type=authorization_code"
                            + "&client_id=react-spa-client"
                            + "&code="
                            + code
                            + "&redirect_uri=http://127.0.0.1:3000/callback"
                            + "&code_verifier="
                            + verifier))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access_token").isNotEmpty())
            .andReturn();

    JsonNode tokenJson = objectMapper.readTree(tokenResult.getResponse().getContentAsString());
    String accessToken = tokenJson.get("access_token").asText();

    mockMvc
        .perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId.toString()))
        .andExpect(jsonPath("$.username").value("alice"));
  }

  private String pkceS256(String verifier) throws Exception {
    byte[] digest =
        MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private String extractQueryParam(String query, String key) {
    int start = 0;
    while (start <= query.length()) {
      int amp = query.indexOf('&', start);
      String pair = amp >= 0 ? query.substring(start, amp) : query.substring(start);
      int eq = pair.indexOf('=');
      if (eq > 0 && pair.substring(0, eq).equals(key)) {
        return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
      }
      if (amp < 0) {
        break;
      }
      start = amp + 1;
    }
    throw new IllegalStateException("Missing query param: " + key);
  }
}
