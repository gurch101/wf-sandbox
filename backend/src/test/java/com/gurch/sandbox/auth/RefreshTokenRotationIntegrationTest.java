package com.gurch.sandbox.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RefreshTokenRotationIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void shouldRotateRefreshTokenAndRevokeFamilyOnReuse() throws Exception {
    UUID userId = UUID.fromString("44444444-4444-4444-4444-444444444444");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", userId, "username", "bob", "email", "bob@local.invalid"));
    jdbcTemplate.update(
        """
        INSERT INTO oauth_clients (client_id, client_secret_hash, grant_types, scopes, redirect_uris, enabled)
        VALUES (:clientId, :secret, :grantTypes, :scopes, :redirectUris, true)
        """,
        Map.of(
            "clientId", "react-rotation-client",
            "secret", "{noop}rotation-secret",
            "grantTypes", "authorization_code refresh_token",
            "scopes", "api.read",
            "redirectUris", "http://127.0.0.1:3000/callback"));

    String verifier = "rotation-verifier-1234567890";
    String code = authorize("react-rotation-client", verifier, "bob");

    JsonNode token1 =
        tokenForAuthorizationCode(
            "react-rotation-client",
            "rotation-secret",
            code,
            verifier,
            "http://127.0.0.1:3000/callback");
    String refresh1 = token1.get("refresh_token").asText();

    JsonNode token2 =
        refresh("react-rotation-client", "rotation-secret", refresh1, status().isOk());
    String refresh2 = token2.get("refresh_token").asText();
    assertThat(refresh2).isNotEqualTo(refresh1);

    JsonNode reuseResponse =
        refresh("react-rotation-client", "rotation-secret", refresh1, status().isBadRequest());
    assertThat(reuseResponse.get("error").asText()).isEqualTo("invalid_grant");

    JsonNode revokedFamilyResponse =
        refresh("react-rotation-client", "rotation-secret", refresh2, status().isBadRequest());
    assertThat(revokedFamilyResponse.get("error").asText()).isEqualTo("invalid_grant");

    Boolean revoked =
        jdbcTemplate.queryForObject(
            "SELECT revoked FROM oauth_refresh_token_families WHERE user_id = :userId AND client_id = :clientId",
            Map.of("userId", userId, "clientId", "react-rotation-client"),
            Boolean.class);
    assertThat(revoked).isTrue();
  }

  private String authorize(String clientId, String verifier, String username) throws Exception {
    String challenge = pkceS256(verifier);
    MvcResult authorizeResult =
        mockMvc
            .perform(
                get("/oauth2/authorize?response_type=code"
                        + "&client_id="
                        + clientId
                        + "&redirect_uri=http://127.0.0.1:3000/callback"
                        + "&scope=api.read"
                        + "&code_challenge="
                        + challenge
                        + "&code_challenge_method=S256"
                        + "&state=rotation-state")
                    .with(user(username)))
            .andExpect(status().is3xxRedirection())
            .andReturn();
    String location = authorizeResult.getResponse().getHeader("Location");
    return extractQueryParam(URI.create(location).getRawQuery(), "code");
  }

  private JsonNode tokenForAuthorizationCode(
      String clientId, String clientSecret, String code, String verifier, String redirectUri)
      throws Exception {
    MvcResult tokenResult =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", basic(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(
                        "grant_type=authorization_code"
                            + "&client_id="
                            + clientId
                            + "&code="
                            + code
                            + "&redirect_uri="
                            + redirectUri
                            + "&code_verifier="
                            + verifier))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(tokenResult.getResponse().getContentAsString());
  }

  private JsonNode refresh(
      String clientId,
      String clientSecret,
      String refreshToken,
      org.springframework.test.web.servlet.ResultMatcher expectedStatus)
      throws Exception {
    MvcResult refreshResult =
        mockMvc
            .perform(
                post("/oauth2/token")
                    .header("Authorization", basic(clientId, clientSecret))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .content(
                        "grant_type=refresh_token"
                            + "&client_id="
                            + clientId
                            + "&refresh_token="
                            + refreshToken))
            .andExpect(expectedStatus)
            .andReturn();
    return objectMapper.readTree(refreshResult.getResponse().getContentAsString());
  }

  private String basic(String clientId, String secret) {
    String raw = clientId + ":" + secret;
    return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
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
