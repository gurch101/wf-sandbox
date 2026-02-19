package com.gurch.sandbox.auth.internal;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BootstrapSecurityRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final UserLookupRepository userLookupRepository;
  private final RoleLookupRepository roleLookupRepository;
  private final PermissionLookupRepository permissionLookupRepository;

  public BootstrapSecurityRepository(
      NamedParameterJdbcTemplate jdbcTemplate,
      UserLookupRepository userLookupRepository,
      RoleLookupRepository roleLookupRepository,
      PermissionLookupRepository permissionLookupRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.userLookupRepository = userLookupRepository;
    this.roleLookupRepository = roleLookupRepository;
    this.permissionLookupRepository = permissionLookupRepository;
  }

  public Optional<UUID> findUserIdByUsername(String username) {
    return userLookupRepository.findByUsername(username).map(AuthUserEntity::id);
  }

  public Optional<UUID> findRoleIdByCode(String code) {
    return roleLookupRepository.findByCode(code).map(RoleEntity::id);
  }

  public Optional<UUID> findPermissionIdByCode(String code) {
    return permissionLookupRepository.findByCode(code).map(PermissionEntity::id);
  }

  public void insertUser(UUID userId, String username, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, :now, :now)
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of(
            "id",
            userId,
            "username",
            username,
            "email",
            email,
            "now",
            java.sql.Timestamp.from(Instant.now())));
  }

  public void upsertCredential(UUID userId, String passwordHash) {
    jdbcTemplate.update(
        """
        INSERT INTO user_credentials (user_id, password_hash, password_updated_at)
        VALUES (:userId, :passwordHash, :updatedAt)
        ON CONFLICT (user_id) DO UPDATE SET
          password_hash = EXCLUDED.password_hash,
          password_updated_at = EXCLUDED.password_updated_at
        """,
        Map.of(
            "userId", userId,
            "passwordHash", passwordHash,
            "updatedAt", java.sql.Timestamp.from(Instant.now())));
  }

  public UUID insertRoleIfMissing(String code, String name) {
    return findRoleIdByCode(code)
        .orElseGet(
            () -> {
              UUID roleId = UUID.randomUUID();
              jdbcTemplate.update(
                  """
                  INSERT INTO roles (id, code, name, created_at, updated_at)
                  VALUES (:id, :code, :name, :now, :now)
                  """,
                  Map.of(
                      "id", roleId,
                      "code", code,
                      "name", name,
                      "now", java.sql.Timestamp.from(Instant.now())));
              return roleId;
            });
  }

  public UUID insertPermissionIfMissing(String code, String description) {
    return findPermissionIdByCode(code)
        .orElseGet(
            () -> {
              UUID permissionId = UUID.randomUUID();
              jdbcTemplate.update(
                  """
                  INSERT INTO permissions (id, code, description, created_at, updated_at)
                  VALUES (:id, :code, :description, :now, :now)
                  """,
                  Map.of(
                      "id", permissionId,
                      "code", code,
                      "description", description,
                      "now", java.sql.Timestamp.from(Instant.now())));
              return permissionId;
            });
  }

  public void linkRolePermission(UUID roleId, UUID permissionId) {
    jdbcTemplate.update(
        """
        INSERT INTO role_permissions (role_id, permission_id, created_at)
        VALUES (:roleId, :permissionId, :createdAt)
        ON CONFLICT (role_id, permission_id) DO NOTHING
        """,
        Map.of(
            "roleId", roleId,
            "permissionId", permissionId,
            "createdAt", java.sql.Timestamp.from(Instant.now())));
  }

  public void linkUserRole(UUID userId, UUID roleId) {
    jdbcTemplate.update(
        """
        INSERT INTO user_roles (user_id, role_id, created_at)
        VALUES (:userId, :roleId, :createdAt)
        ON CONFLICT (user_id, role_id) DO NOTHING
        """,
        Map.of(
            "userId", userId,
            "roleId", roleId,
            "createdAt", java.sql.Timestamp.from(Instant.now())));
  }
}
