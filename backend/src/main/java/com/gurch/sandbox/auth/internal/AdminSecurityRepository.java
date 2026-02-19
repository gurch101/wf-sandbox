package com.gurch.sandbox.auth.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AdminSecurityRepository {

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final UserLookupRepository userLookupRepository;
  private final RoleLookupRepository roleLookupRepository;
  private final PermissionLookupRepository permissionLookupRepository;
  private final WorkflowGroupLookupRepository workflowGroupLookupRepository;

  public AdminSecurityRepository(
      NamedParameterJdbcTemplate jdbcTemplate,
      UserLookupRepository userLookupRepository,
      RoleLookupRepository roleLookupRepository,
      PermissionLookupRepository permissionLookupRepository,
      WorkflowGroupLookupRepository workflowGroupLookupRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.userLookupRepository = userLookupRepository;
    this.roleLookupRepository = roleLookupRepository;
    this.permissionLookupRepository = permissionLookupRepository;
    this.workflowGroupLookupRepository = workflowGroupLookupRepository;
  }

  public Optional<UUID> findUserId(UUID userId) {
    return userLookupRepository.findById(userId).map(AuthUserEntity::id);
  }

  public Optional<UUID> findRoleIdByCode(String roleCode) {
    return roleLookupRepository.findByCode(roleCode).map(RoleEntity::id);
  }

  public Optional<RoleEntity> findRoleByCode(String roleCode) {
    return roleLookupRepository.findByCode(roleCode);
  }

  public List<RoleEntity> findRoles(String codePattern, int page, int size) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("r.id, r.code, r.name, r.created_at, r.updated_at")
            .from("roles", "r")
            .where("upper(r.code)", Operator.LIKE, codePattern)
            .orderBy("+r.code")
            .page(page, size);
    BuiltQuery query = builder.build();
    return jdbcTemplate.query(
        query.sql(),
        query.params(),
        (rs, rowNum) ->
            new RoleEntity(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
  }

  public long countRoles(String codePattern) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("roles", "r")
            .where("upper(r.code)", Operator.LIKE, codePattern)
            .build();
    return nullableLongToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Long.class));
  }

  public Optional<UUID> findPermissionIdByCode(String permissionCode) {
    return permissionLookupRepository.findByCode(permissionCode).map(PermissionEntity::id);
  }

  public Optional<PermissionEntity> findPermissionByCode(String permissionCode) {
    return permissionLookupRepository.findByCode(permissionCode);
  }

  public List<PermissionEntity> findPermissions(String codePattern, int page, int size) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("p.id, p.code, p.description, p.created_at, p.updated_at")
            .from("permissions", "p")
            .where("upper(p.code)", Operator.LIKE, codePattern)
            .orderBy("+p.code")
            .page(page, size);
    BuiltQuery query = builder.build();
    return jdbcTemplate.query(
        query.sql(),
        query.params(),
        (rs, rowNum) ->
            new PermissionEntity(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("description"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
  }

  public long countPermissions(String codePattern) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("permissions", "p")
            .where("upper(p.code)", Operator.LIKE, codePattern)
            .build();
    return nullableLongToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Long.class));
  }

  public Optional<UUID> findWorkflowGroupIdByCode(String workflowGroupCode) {
    return workflowGroupLookupRepository.findByCode(workflowGroupCode).map(WorkflowGroupEntity::id);
  }

  public Optional<WorkflowGroupEntity> findWorkflowGroupByCode(String workflowGroupCode) {
    return workflowGroupLookupRepository.findByCode(workflowGroupCode);
  }

  public List<WorkflowGroupEntity> findWorkflowGroups(String codePattern, int page, int size) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("wg.id, wg.code, wg.name, wg.created_at, wg.updated_at")
            .from("workflow_groups", "wg")
            .where("upper(wg.code)", Operator.LIKE, codePattern)
            .orderBy("+wg.code")
            .page(page, size);
    BuiltQuery query = builder.build();
    return jdbcTemplate.query(
        query.sql(),
        query.params(),
        (rs, rowNum) ->
            new WorkflowGroupEntity(
                (UUID) rs.getObject("id"),
                rs.getString("code"),
                rs.getString("name"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()));
  }

  public long countWorkflowGroups(String codePattern) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("workflow_groups", "wg")
            .where("upper(wg.code)", Operator.LIKE, codePattern)
            .build();
    return nullableLongToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Long.class));
  }

  public UUID createRole(String code, String name) {
    UUID roleId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        INSERT INTO roles (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, :now, :now)
        """,
        Map.of("id", roleId, "code", code, "name", name, "now", now));
    return roleId;
  }

  public UUID createPermission(String code, String description) {
    UUID permissionId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        INSERT INTO permissions (id, code, description, created_at, updated_at)
        VALUES (:id, :code, :description, :now, :now)
        """,
        Map.of("id", permissionId, "code", code, "description", description, "now", now));
    return permissionId;
  }

  public UUID createWorkflowGroup(String code, String name) {
    UUID groupId = UUID.randomUUID();
    Timestamp now = Timestamp.from(Instant.now());
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, :now, :now)
        """,
        Map.of("id", groupId, "code", code, "name", name, "now", now));
    return groupId;
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
            "createdAt", Timestamp.from(Instant.now())));
  }

  public int unlinkRolePermission(UUID roleId, UUID permissionId) {
    return jdbcTemplate.update(
        """
        DELETE FROM role_permissions
        WHERE role_id = :roleId AND permission_id = :permissionId
        """,
        Map.of("roleId", roleId, "permissionId", permissionId));
  }

  public void linkUserRole(UUID userId, UUID roleId) {
    jdbcTemplate.update(
        """
        INSERT INTO user_roles (user_id, role_id, created_at)
        VALUES (:userId, :roleId, :createdAt)
        ON CONFLICT (user_id, role_id) DO NOTHING
        """,
        Map.of("userId", userId, "roleId", roleId, "createdAt", Timestamp.from(Instant.now())));
  }

  public int unlinkUserRole(UUID userId, UUID roleId) {
    return jdbcTemplate.update(
        """
        DELETE FROM user_roles
        WHERE user_id = :userId AND role_id = :roleId
        """,
        Map.of("userId", userId, "roleId", roleId));
  }

  public void linkUserWorkflowGroup(UUID userId, UUID workflowGroupId) {
    jdbcTemplate.update(
        """
        INSERT INTO user_workflow_groups (user_id, workflow_group_id, created_at)
        VALUES (:userId, :workflowGroupId, :createdAt)
        ON CONFLICT (user_id, workflow_group_id) DO NOTHING
        """,
        Map.of(
            "userId", userId,
            "workflowGroupId", workflowGroupId,
            "createdAt", Timestamp.from(Instant.now())));
  }

  public int unlinkUserWorkflowGroup(UUID userId, UUID workflowGroupId) {
    return jdbcTemplate.update(
        """
        DELETE FROM user_workflow_groups
        WHERE user_id = :userId AND workflow_group_id = :workflowGroupId
        """,
        Map.of("userId", userId, "workflowGroupId", workflowGroupId));
  }

  public void linkPrincipalClientScope(UUID userId, String businessClientId) {
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, :createdAt)
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of(
            "id",
            UUID.randomUUID(),
            "principalUserId",
            userId,
            "businessClientId",
            businessClientId,
            "createdAt",
            Timestamp.from(Instant.now())));
  }

  public int unlinkPrincipalClientScope(UUID userId, String businessClientId) {
    return jdbcTemplate.update(
        """
        DELETE FROM principal_client_scopes
        WHERE principal_user_id = :userId AND business_client_id = :businessClientId
        """,
        Map.of("userId", userId, "businessClientId", businessClientId));
  }

  public List<String> findRoleCodesByUserId(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("r.code")
            .from("user_roles", "ur")
            .join(com.gurch.sandbox.query.JoinType.INNER, "roles", "r", "r.id = ur.role_id")
            .where("ur.user_id", Operator.EQ, userId)
            .orderBy("+r.code")
            .build();
    return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("code"));
  }

  public List<String> findPermissionCodesByRoleId(UUID roleId) {
    BuiltQuery query =
        SQLQueryBuilder.select("p.code")
            .from("role_permissions", "rp")
            .join(
                com.gurch.sandbox.query.JoinType.INNER,
                "permissions",
                "p",
                "p.id = rp.permission_id")
            .where("rp.role_id", Operator.EQ, roleId)
            .orderBy("+p.code")
            .build();
    return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("code"));
  }

  public List<String> findWorkflowGroupCodesByUserId(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("wg.code")
            .from("user_workflow_groups", "uwg")
            .join(
                com.gurch.sandbox.query.JoinType.INNER,
                "workflow_groups",
                "wg",
                "wg.id = uwg.workflow_group_id")
            .where("uwg.user_id", Operator.EQ, userId)
            .orderBy("+wg.code")
            .build();
    return jdbcTemplate.query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("code"));
  }

  public List<String> findClientScopeIdsByUserId(UUID userId) {
    BuiltQuery query =
        SQLQueryBuilder.select("pcs.business_client_id")
            .from("principal_client_scopes", "pcs")
            .where("pcs.principal_user_id", Operator.EQ, userId)
            .orderBy("+pcs.business_client_id")
            .build();
    return jdbcTemplate.query(
        query.sql(), query.params(), (rs, rowNum) -> rs.getString("business_client_id"));
  }

  public int countUserRoleAssignments(UUID roleId) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("user_roles", "ur")
            .where("ur.role_id", Operator.EQ, roleId)
            .build();
    return nullableIntToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Integer.class));
  }

  public int countRolePermissionAssignments(UUID roleId) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("role_permissions", "rp")
            .where("rp.role_id", Operator.EQ, roleId)
            .build();
    return nullableIntToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Integer.class));
  }

  public int countPermissionRoleAssignments(UUID permissionId) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("role_permissions", "rp")
            .where("rp.permission_id", Operator.EQ, permissionId)
            .build();
    return nullableIntToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Integer.class));
  }

  public int countWorkflowGroupUserAssignments(UUID workflowGroupId) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("user_workflow_groups", "uwg")
            .where("uwg.workflow_group_id", Operator.EQ, workflowGroupId)
            .build();
    return nullableIntToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Integer.class));
  }

  public int countRequestWorkflowGroupReferences(String workflowGroupCode) {
    BuiltQuery query =
        SQLQueryBuilder.select("count(*)")
            .from("requests", "r")
            .where("r.workflow_group_code", Operator.EQ, workflowGroupCode)
            .build();
    return nullableIntToPrimitive(
        jdbcTemplate.queryForObject(query.sql(), query.params(), Integer.class));
  }

  private static long nullableLongToPrimitive(Long value) {
    return value == null ? 0L : value;
  }

  private static int nullableIntToPrimitive(Integer value) {
    return value == null ? 0 : value;
  }

  public int deleteRoleByCode(String roleCode) {
    return jdbcTemplate.update("DELETE FROM roles WHERE code = :code", Map.of("code", roleCode));
  }

  public int updateRoleNameByCode(String roleCode, String name) {
    return jdbcTemplate.update(
        """
        UPDATE roles
        SET name = :name,
            updated_at = :updatedAt
        WHERE code = :code
        """,
        Map.of(
            "code", roleCode,
            "name", name,
            "updatedAt", Timestamp.from(Instant.now())));
  }

  public int deletePermissionByCode(String permissionCode) {
    return jdbcTemplate.update(
        "DELETE FROM permissions WHERE code = :code", Map.of("code", permissionCode));
  }

  public int updatePermissionDescriptionByCode(String permissionCode, String description) {
    return jdbcTemplate.update(
        """
        UPDATE permissions
        SET description = :description,
            updated_at = :updatedAt
        WHERE code = :code
        """,
        Map.of(
            "code", permissionCode,
            "description", description,
            "updatedAt", Timestamp.from(Instant.now())));
  }

  public int deleteWorkflowGroupByCode(String workflowGroupCode) {
    return jdbcTemplate.update(
        "DELETE FROM workflow_groups WHERE code = :code", Map.of("code", workflowGroupCode));
  }

  public int updateWorkflowGroupNameByCode(String workflowGroupCode, String name) {
    return jdbcTemplate.update(
        """
        UPDATE workflow_groups
        SET name = :name,
            updated_at = :updatedAt
        WHERE code = :code
        """,
        Map.of(
            "code", workflowGroupCode,
            "name", name,
            "updatedAt", Timestamp.from(Instant.now())));
  }
}
