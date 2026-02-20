package com.gurch.sandbox.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

class AdminApiIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  @WithMockUser
  void shouldRejectRoleCreationWithoutAdminPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/roles")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.CreateRoleRequest("OPS", "Ops Role"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldCreateRoleWhenCallerHasAdminPermission() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/roles")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.CreateRoleRequest("REQUEST_OPERATOR", "Request Operator"))))
        .andExpect(status().isCreated());

    UUID roleId =
        jdbcTemplate.queryForObject(
            "SELECT id FROM roles WHERE code = :code",
            Map.of("code", "REQUEST_OPERATOR"),
            UUID.class);
    assertThat(roleId).isNotNull();
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnConflictWhenCreatingDuplicateRoleCode() throws Exception {
    jdbcTemplate.update(
        """
        INSERT INTO roles (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        """,
        Map.of(
            "id", UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"),
            "code", "DUPLICATE_ROLE",
            "name", "Existing Role"));

    mockMvc
        .perform(
            post("/api/admin/roles")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.CreateRoleRequest("DUPLICATE_ROLE", "Duplicate Role"))))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldListRolesWithPaginationAndCodeFilter() throws Exception {
    insertRole(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"), "OPS_ADMIN", "Ops Admin");
    insertRole(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"), "OPS_VIEWER", "Ops Viewer");
    insertRole(UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"), "HR_ADMIN", "Hr Admin");

    mockMvc
        .perform(
            get("/api/admin/roles")
                .param("codeContains", "OPS")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(1))
        .andExpect(jsonPath("$.roles.length()").value(1))
        .andExpect(jsonPath("$.roles[0].code").value("OPS_ADMIN"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldGetRoleByCode() throws Exception {
    insertRole(UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"), "REQ_OPERATOR", "Req Op");

    mockMvc
        .perform(get("/api/admin/roles/{roleCode}", "REQ_OPERATOR"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("REQ_OPERATOR"))
        .andExpect(jsonPath("$.name").value("Req Op"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldUpdateRoleName() throws Exception {
    insertRole(
        UUID.fromString("abababab-abab-abab-abab-abababababab"), "ROLE_TO_UPDATE", "Old Name");

    mockMvc
        .perform(
            put("/api/admin/roles/{roleCode}", "ROLE_TO_UPDATE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new AdminDtos.UpdateRoleRequest("New Name"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("ROLE_TO_UPDATE"))
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnNotFoundWhenUpdatingMissingRole() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/roles/{roleCode}", "MISSING_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDtos.UpdateRoleRequest("Name"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnNotFoundForUnknownRoleCode() throws Exception {
    mockMvc
        .perform(get("/api/admin/roles/{roleCode}", "MISSING_ROLE"))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldDeleteRoleWhenUnassigned() throws Exception {
    insertRole(UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), "TEMP_ROLE", "Temp");

    mockMvc
        .perform(
            delete("/api/admin/roles/{roleCode}", "TEMP_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT count(*) FROM roles WHERE code = :code",
            Map.of("code", "TEMP_ROLE"),
            Integer.class);
    assertThat(count).isZero();
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnConflictWhenDeletingRoleInUse() throws Exception {
    UUID userId = UUID.fromString("12121212-1212-1212-1212-121212121212");
    insertRole(UUID.fromString("11111111-aaaa-bbbb-cccc-111111111111"), "IN_USE_ROLE", "In Use");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", userId, "username", "in-use-user", "email", "in-use-user@local.invalid"));
    mockMvc
        .perform(
            put("/api/admin/users/{userId}/roles/{roleCode}", userId, "IN_USE_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/admin/roles/{roleCode}", "IN_USE_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldListPermissionsWithPaginationAndCodeFilter() throws Exception {
    insertPermission(
        UUID.fromString("22222222-aaaa-bbbb-cccc-222222222222"), "request.read", "Read requests");
    insertPermission(
        UUID.fromString("33333333-aaaa-bbbb-cccc-333333333333"), "request.write", "Write requests");
    insertPermission(
        UUID.fromString("44444444-aaaa-bbbb-cccc-444444444444"), "task.complete", "Complete tasks");

    mockMvc
        .perform(
            get("/api/admin/permissions")
                .param("codeContains", "request")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.permissions.length()").value(1))
        .andExpect(jsonPath("$.permissions[0].code").value("request.read"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldGetPermissionByCode() throws Exception {
    insertPermission(
        UUID.fromString("55555555-aaaa-bbbb-cccc-555555555555"),
        "permission.unique.read",
        "Unique permission");

    mockMvc
        .perform(get("/api/admin/permissions/{permissionCode}", "permission.unique.read"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("permission.unique.read"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldUpdatePermissionDescription() throws Exception {
    insertPermission(
        UUID.fromString("cdcdcdcd-cdcd-cdcd-cdcd-cdcdcdcdcdcd"),
        "permission.to.update",
        "Old description");

    mockMvc
        .perform(
            put("/api/admin/permissions/{permissionCode}", "permission.to.update")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.UpdatePermissionRequest("New description"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("permission.to.update"))
        .andExpect(jsonPath("$.description").value("New description"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnNotFoundWhenUpdatingMissingPermission() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/permissions/{permissionCode}", "permission.missing")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.UpdatePermissionRequest("Description"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldDeletePermissionWhenUnassigned() throws Exception {
    insertPermission(
        UUID.fromString("66666666-aaaa-bbbb-cccc-666666666666"),
        "temp.permission",
        "Temp permission");

    mockMvc
        .perform(
            delete("/api/admin/permissions/{permissionCode}", "temp.permission")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnConflictWhenDeletingPermissionInUse() throws Exception {
    UUID roleId = UUID.fromString("77777777-aaaa-bbbb-cccc-777777777777");
    UUID permissionId = UUID.fromString("88888888-aaaa-bbbb-cccc-888888888888");
    insertRole(roleId, "PERM_ROLE", "Permission Role");
    insertPermission(permissionId, "perm.in.use", "Permission In Use");
    mockMvc
        .perform(
            put(
                    "/api/admin/roles/{roleCode}/permissions/{permissionCode}",
                    "PERM_ROLE",
                    "perm.in.use")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/admin/permissions/{permissionCode}", "perm.in.use")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldListWorkflowGroupsWithPaginationAndCodeFilter() throws Exception {
    insertWorkflowGroup(
        UUID.fromString("99999999-aaaa-bbbb-cccc-999999999999"), "ADM_WF_APPROVERS", "Approvers");
    insertWorkflowGroup(
        UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"), "ADM_WF_EXECUTORS", "Executors");
    insertWorkflowGroup(
        UUID.fromString("bbbbbbbb-bbbb-cccc-dddd-eeeeeeeeeeee"), "HR_GROUP", "HR Group");

    mockMvc
        .perform(
            get("/api/admin/workflow-groups")
                .param("codeContains", "WF_")
                .param("page", "0")
                .param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(2))
        .andExpect(jsonPath("$.workflowGroups.length()").value(1))
        .andExpect(jsonPath("$.workflowGroups[0].code").value("ADM_WF_APPROVERS"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldGetWorkflowGroupByCode() throws Exception {
    insertWorkflowGroup(
        UUID.fromString("cccccccc-bbbb-cccc-dddd-eeeeeeeeeeee"), "WF_TEST", "WF Test");

    mockMvc
        .perform(get("/api/admin/workflow-groups/{workflowGroupCode}", "WF_TEST"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("WF_TEST"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldUpdateWorkflowGroupName() throws Exception {
    insertWorkflowGroup(
        UUID.fromString("efefefef-efef-efef-efef-efefefefefef"), "WF_UPDATE", "Old Group Name");

    mockMvc
        .perform(
            put("/api/admin/workflow-groups/{workflowGroupCode}", "WF_UPDATE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.UpdateWorkflowGroupRequest("New Group Name"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("WF_UPDATE"))
        .andExpect(jsonPath("$.name").value("New Group Name"));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnNotFoundWhenUpdatingMissingWorkflowGroup() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/workflow-groups/{workflowGroupCode}", "WF_MISSING")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new AdminDtos.UpdateWorkflowGroupRequest("Name"))))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldDeleteWorkflowGroupWhenUnassigned() throws Exception {
    insertWorkflowGroup(
        UUID.fromString("dddddddd-bbbb-cccc-dddd-eeeeeeeeeeee"), "WF_TEMP", "WF Temp");

    mockMvc
        .perform(
            delete("/api/admin/workflow-groups/{workflowGroupCode}", "WF_TEMP")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldReturnConflictWhenDeletingWorkflowGroupInUse() throws Exception {
    UUID userId = UUID.fromString("34343434-3434-3434-3434-343434343434");
    UUID workflowGroupId = UUID.fromString("eeeeeeee-bbbb-cccc-dddd-eeeeeeeeeeee");
    insertWorkflowGroup(workflowGroupId, "WF_IN_USE", "WF In Use");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", userId, "username", "wf-user", "email", "wf-user@local.invalid"));
    mockMvc
        .perform(
            put(
                    "/api/admin/users/{userId}/workflow-groups/{workflowGroupCode}",
                    userId,
                    "WF_IN_USE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            delete("/api/admin/workflow-groups/{workflowGroupCode}", "WF_IN_USE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldListAndUnassignRolePermissionAssignments() throws Exception {
    UUID roleId = UUID.fromString("f1111111-aaaa-bbbb-cccc-111111111111");
    UUID permissionId = UUID.fromString("f2222222-aaaa-bbbb-cccc-222222222222");
    insertRole(roleId, "ASSIGN_ROLE", "Assign Role");
    insertPermission(permissionId, "assign.permission", "Assign Permission");
    mockMvc
        .perform(
            put(
                    "/api/admin/roles/{roleCode}/permissions/{permissionCode}",
                    "ASSIGN_ROLE",
                    "assign.permission")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/roles/{roleCode}/permissions", "ASSIGN_ROLE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissionCodes.length()").value(1))
        .andExpect(jsonPath("$.permissionCodes[0]").value("assign.permission"));

    mockMvc
        .perform(
            delete(
                    "/api/admin/roles/{roleCode}/permissions/{permissionCode}",
                    "ASSIGN_ROLE",
                    "assign.permission")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/roles/{roleCode}/permissions", "ASSIGN_ROLE"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.permissionCodes.length()").value(0));
  }

  @Test
  @WithMockUser(authorities = "admin.security.manage")
  void shouldListAndUnassignUserAssignmentsAndScopes() throws Exception {
    UUID userId = UUID.fromString("f3333333-aaaa-bbbb-cccc-333333333333");
    insertUser(userId, "assignment-user", "assignment-user@local.invalid");
    insertRole(
        UUID.fromString("f4444444-aaaa-bbbb-cccc-444444444444"),
        "USER_ASSIGN_ROLE",
        "User Assign Role");
    insertWorkflowGroup(
        UUID.fromString("f5555555-aaaa-bbbb-cccc-555555555555"),
        "USER_ASSIGN_WF",
        "User Assign Wf");

    mockMvc
        .perform(
            put("/api/admin/users/{userId}/roles/{roleCode}", userId, "USER_ASSIGN_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            put(
                    "/api/admin/users/{userId}/workflow-groups/{workflowGroupCode}",
                    userId,
                    "USER_ASSIGN_WF")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            put("/api/admin/users/{userId}/client-scopes/{businessClientId}", userId, "CLIENT_Z")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/users/{userId}/roles", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleCodes[0]").value("USER_ASSIGN_ROLE"));
    mockMvc
        .perform(get("/api/admin/users/{userId}/workflow-groups", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowGroupCodes[0]").value("USER_ASSIGN_WF"));
    mockMvc
        .perform(get("/api/admin/users/{userId}/client-scopes", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientScopeIds[0]").value("CLIENT_Z"));

    mockMvc
        .perform(
            delete("/api/admin/users/{userId}/roles/{roleCode}", userId, "USER_ASSIGN_ROLE")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete(
                    "/api/admin/users/{userId}/workflow-groups/{workflowGroupCode}",
                    userId,
                    "USER_ASSIGN_WF")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
    mockMvc
        .perform(
            delete("/api/admin/users/{userId}/client-scopes/{businessClientId}", userId, "CLIENT_Z")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/users/{userId}/roles", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleCodes.length()").value(0));
    mockMvc
        .perform(get("/api/admin/users/{userId}/workflow-groups", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.workflowGroupCodes.length()").value(0));
    mockMvc
        .perform(get("/api/admin/users/{userId}/client-scopes", userId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clientScopeIds.length()").value(0));
  }

  private void insertRole(UUID id, String code, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO roles (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        """,
        Map.of("id", id, "code", code, "name", name));
  }

  private void insertPermission(UUID id, String code, String description) {
    jdbcTemplate.update(
        """
        INSERT INTO permissions (id, code, description, created_at, updated_at)
        VALUES (:id, :code, :description, now(), now())
        """,
        Map.of("id", id, "code", code, "description", description));
  }

  private void insertWorkflowGroup(UUID id, String code, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        """,
        Map.of("id", id, "code", code, "name", name));
  }

  private void insertUser(UUID id, String username, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", id, "username", username, "email", email));
  }
}
