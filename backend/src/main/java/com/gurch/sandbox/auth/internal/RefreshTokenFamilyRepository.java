package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.JoinType;
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
import org.springframework.core.convert.ConversionService;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RefreshTokenFamilyRepository {

  private static final String SHA_256 = "SHA-256";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final DataClassRowMapper<RefreshTokenRecord> refreshTokenRecordRowMapper;

  public RefreshTokenFamilyRepository(
      NamedParameterJdbcTemplate jdbcTemplate, ConversionService conversionService) {
    this.jdbcTemplate = jdbcTemplate;
    this.refreshTokenRecordRowMapper = DataClassRowMapper.newInstance(RefreshTokenRecord.class);
    this.refreshTokenRecordRowMapper.setConversionService(conversionService);
  }

  /**
   * Looks up the token family metadata for a refresh token hash.
   *
   * <p>A token family represents the lifecycle of refresh-token rotation for one user/client pair.
   */
  public Optional<RefreshTokenRecord> findByTokenHash(String tokenHash) {
    BuiltQuery query =
        SQLQueryBuilder.select("rt.family_id, rt.is_active as active, f.revoked as revoked")
            .from("oauth_refresh_tokens", "rt")
            .join(JoinType.INNER, "oauth_refresh_token_families", "f", "f.id = rt.family_id")
            .where("rt.token_hash", Operator.EQ, tokenHash)
            .build();

    return jdbcTemplate.query(query.sql(), query.params(), refreshTokenRecordRowMapper).stream()
        .findFirst();
  }

  public boolean isFamilyRevoked(UUID familyId) {
    BuiltQuery query =
        SQLQueryBuilder.select("f.revoked")
            .from("oauth_refresh_token_families", "f")
            .where("f.id", Operator.EQ, familyId)
            .build();

    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Boolean.class));
  }

  @Transactional
  public UUID createOrGetFamily(UUID userId, String clientId, Instant expiresAt) {
    BuiltQuery query =
        SQLQueryBuilder.select("f.id")
            .from("oauth_refresh_token_families", "f")
            .where("f.user_id", Operator.EQ, userId)
            .where("f.client_id", Operator.EQ, clientId)
            .build();

    Optional<UUID> existing;
    try {
      existing =
          Optional.ofNullable(jdbcTemplate.queryForObject(query.sql(), query.params(), UUID.class));
    } catch (EmptyResultDataAccessException ignored) {
      existing = Optional.empty();
    }
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

  @Transactional
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

  @Transactional
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
      MessageDigest digest = MessageDigest.getInstance(SHA_256);
      byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to hash refresh token", e);
    }
  }

  public record RefreshTokenRecord(UUID familyId, boolean active, boolean revoked) {}
}
