package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenFamilyRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public RefreshTokenFamilyRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
    BuiltQuery query =
        SQLQueryBuilder.select("rt.family_id, rt.is_active, f.revoked")
            .from("oauth_refresh_tokens", "rt")
            .join(
                com.gurch.sandbox.query.JoinType.INNER,
                "oauth_refresh_token_families",
                "f",
                "f.id = rt.family_id")
            .where("rt.token_hash", Operator.EQ, tokenHash)
            .build();

    return jdbcTemplate
        .query(
            query.sql(),
            query.params(),
            (rs, rowNum) ->
                new RefreshTokenRecord(
                    (UUID) rs.getObject("family_id"),
                    rs.getBoolean("is_active"),
                    rs.getBoolean("revoked")))
        .stream()
        .findFirst();
  }

  public boolean isFamilyRevoked(UUID familyId) {
    BuiltQuery query =
        SQLQueryBuilder.select("f.revoked")
            .from("oauth_refresh_token_families", "f")
            .where("f.id", Operator.EQ, familyId)
            .build();

    return jdbcTemplate
        .query(query.sql(), query.params(), (rs, rowNum) -> rs.getBoolean("revoked"))
        .stream()
        .findFirst()
        .orElse(false);
  }

  public UUID createOrGetFamily(UUID userId, String clientId, Instant expiresAt) {
    BuiltQuery query =
        SQLQueryBuilder.select("f.id")
            .from("oauth_refresh_token_families", "f")
            .where("f.user_id", Operator.EQ, userId)
            .where("f.client_id", Operator.EQ, clientId)
            .build();

    Optional<UUID> existing =
        jdbcTemplate
            .query(query.sql(), query.params(), (rs, rowNum) -> (UUID) rs.getObject("id"))
            .stream()
            .findFirst();
    if (existing.isPresent()) {
      return existing.get();
    }

    UUID familyId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO oauth_refresh_token_families (id, user_id, client_id, revoked, expires_at, updated_at)
        VALUES (:id, :userId, :clientId, false, :expiresAt, now())
        """,
        Map.of(
            "id", familyId,
            "userId", userId,
            "clientId", clientId,
            "expiresAt", Timestamp.from(expiresAt)));
    return familyId;
  }

  public void rotate(
      UUID familyId, String previousTokenHash, String newTokenHash, Instant expiresAt) {
    if (previousTokenHash != null) {
      jdbcTemplate.update(
          """
          UPDATE oauth_refresh_tokens
          SET is_active = false, used_at = now()
          WHERE token_hash = :tokenHash
          """,
          Map.of("tokenHash", previousTokenHash));
    }

    jdbcTemplate.update(
        """
        INSERT INTO oauth_refresh_tokens (token_hash, family_id, is_active, issued_at, used_at)
        VALUES (:tokenHash, :familyId, true, now(), null)
        ON CONFLICT (token_hash) DO UPDATE SET is_active = EXCLUDED.is_active, used_at = null
        """,
        Map.of("tokenHash", newTokenHash, "familyId", familyId));

    jdbcTemplate.update(
        """
        UPDATE oauth_refresh_token_families
        SET updated_at = now(), expires_at = :expiresAt
        WHERE id = :familyId
        """,
        Map.of("familyId", familyId, "expiresAt", Timestamp.from(expiresAt)));
  }

  public void revokeFamily(UUID familyId) {
    jdbcTemplate.update(
        """
        UPDATE oauth_refresh_token_families
        SET revoked = true, updated_at = now()
        WHERE id = :familyId
        """,
        Map.of("familyId", familyId));
    jdbcTemplate.update(
        """
        UPDATE oauth_refresh_tokens
        SET is_active = false, used_at = coalesce(used_at, now())
        WHERE family_id = :familyId
        """,
        Map.of("familyId", familyId));
  }

  public String hashToken(String token) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash refresh token", e);
    }
  }

  public record RefreshTokenRecord(UUID familyId, boolean active, boolean revoked) {}
}
