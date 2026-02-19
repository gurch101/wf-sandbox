package com.gurch.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class BootstrapAdminInitializationIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void shouldCreateBootstrapAdminUserRoleAndAssignmentOnStartup() {
    UUID userId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE username = :username",
            Map.of("username", "admin"),
            UUID.class);
    assertThat(userId).isNotNull();

    String passwordHash =
        jdbcTemplate.queryForObject(
            """
            SELECT password_hash
            FROM user_credentials
            WHERE user_id = :userId
            """,
            Map.of("userId", userId),
            String.class);
    assertThat(passwordHash).isNotBlank();
    assertThat(passwordHash).isNotEqualTo("AdminPass123!");

    UUID roleId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = :code",
            Map.of("code", "PLATFORM_ADMIN"),
            UUID.class);
    assertThat(roleId).isNotNull();

    Integer assignments =
        jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM user_roles
            WHERE user_id = :userId AND role_id = :roleId
            """,
            Map.of("userId", userId, "roleId", roleId),
            Integer.class);
    assertThat(assignments).isEqualTo(1);
  }
}
