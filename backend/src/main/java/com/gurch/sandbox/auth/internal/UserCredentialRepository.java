package com.gurch.sandbox.auth.internal;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserCredentialRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public UserCredentialRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<UserCredentialRecord> findEnabledByLogin(String login) {
    return jdbcTemplate
        .query(
            """
            SELECT u.id, u.username, u.email, uc.password_hash
            FROM users u
            INNER JOIN user_credentials uc ON uc.user_id = u.id
            WHERE u.enabled = true
              AND u.is_system = false
              AND (u.username = :login OR u.email = :login)
            ORDER BY CASE WHEN u.username = :login THEN 0 ELSE 1 END
            LIMIT 1
            """,
            Map.of("login", login),
            (rs, rowNum) ->
                new UserCredentialRecord(
                    (UUID) rs.getObject("id"),
                    rs.getString("username"),
                    rs.getString("email"),
                    rs.getString("password_hash")))
        .stream()
        .findFirst();
  }

  public record UserCredentialRecord(
      UUID userId, String username, String email, String passwordHash) {}
}
