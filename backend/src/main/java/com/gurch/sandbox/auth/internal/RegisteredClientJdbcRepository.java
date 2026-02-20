package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.stereotype.Repository;

@Repository
public class RegisteredClientJdbcRepository implements RegisteredClientRepository {

  private static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
  private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(30);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public RegisteredClientJdbcRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public void save(RegisteredClient registeredClient) {
    String grantTypes = String.join(" ", toValueSet(registeredClient.getAuthorizationGrantTypes()));
    String scopes = String.join(" ", registeredClient.getScopes());

    int updated =
        jdbcTemplate.update(
            """
            UPDATE oauth_clients
            SET client_secret_hash = :secret,
                grant_types = :grantTypes,
                scopes = :scopes,
                updated_at = now()
            WHERE client_id = :clientId
            """,
            Map.of(
                "clientId",
                registeredClient.getClientId(),
                "secret",
                registeredClient.getClientSecret(),
                "grantTypes",
                grantTypes,
                "scopes",
                scopes));

    if (updated == 0) {
      jdbcTemplate.update(
          """
          INSERT INTO oauth_clients (
            client_id, client_secret_hash, grant_types, scopes, enabled, created_at, updated_at
          )
          VALUES (:clientId, :secret, :grantTypes, :scopes, true, now(), now())
          """,
          Map.of(
              "clientId",
              registeredClient.getClientId(),
              "secret",
              registeredClient.getClientSecret(),
              "grantTypes",
              grantTypes,
              "scopes",
              scopes));
    }
  }

  @Override
  public RegisteredClient findById(String id) {
    return findByClientId(id);
  }

  @Override
  public RegisteredClient findByClientId(String clientId) {
    BuiltQuery query =
        SQLQueryBuilder.select("client_id, client_secret_hash, grant_types, scopes, redirect_uris")
            .from("oauth_clients", "oc")
            .where("oc.client_id", Operator.EQ, clientId)
            .where("oc.enabled", Operator.EQ, true)
            .build();

    return jdbcTemplate
        .query(
            query.sql(),
            query.params(),
            (rs, rowNum) -> {
              Set<String> grantTypes = splitValues(rs.getString("grant_types"));
              Set<String> scopes = splitValues(rs.getString("scopes"));

              RegisteredClient.Builder builder =
                  RegisteredClient.withId(rs.getString("client_id"))
                      .clientId(rs.getString("client_id"))
                      .tokenSettings(
                          TokenSettings.builder()
                              .accessTokenTimeToLive(ACCESS_TOKEN_TTL)
                              .refreshTokenTimeToLive(REFRESH_TOKEN_TTL)
                              .reuseRefreshTokens(false)
                              .build());

              String secret = rs.getString("client_secret_hash");
              if (secret == null || secret.isBlank()) {
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.NONE);
              } else {
                builder.clientSecret(secret);
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC);
                builder.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST);
              }

              for (String grantType : grantTypes) {
                builder.authorizationGrantType(new AuthorizationGrantType(grantType));
              }
              for (String scope : scopes) {
                builder.scope(scope);
              }
              String redirectUris = rs.getString("redirect_uris");
              if (redirectUris != null && !redirectUris.isBlank()) {
                for (String redirectUri : splitValues(redirectUris)) {
                  builder.redirectUri(redirectUri);
                }
              }

              boolean isPkceClient =
                  grantTypes.contains(AuthorizationGrantType.AUTHORIZATION_CODE.getValue())
                      && (secret == null || secret.isBlank());
              builder.clientSettings(
                  ClientSettings.builder().requireProofKey(isPkceClient).build());
              return builder.build();
            })
        .stream()
        .findFirst()
        .orElse(null);
  }

  private Set<String> splitValues(String input) {
    Set<String> values = new HashSet<>();
    Arrays.stream(input.split("[,\\s]+"))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .forEach(values::add);
    return values;
  }

  private Set<String> toValueSet(Set<AuthorizationGrantType> grantTypes) {
    Set<String> values = new HashSet<>();
    for (AuthorizationGrantType grantType : grantTypes) {
      values.add(grantType.getValue());
    }
    return values;
  }
}
