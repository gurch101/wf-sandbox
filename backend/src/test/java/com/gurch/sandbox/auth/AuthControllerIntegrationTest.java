package com.gurch.sandbox.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

class AuthControllerIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Test
  void shouldReturnUnauthorizedWhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "11111111-1111-1111-1111-111111111111",
      authorities = {"task.complete"})
  void shouldReturnPrincipalContextWhenAuthenticated() throws Exception {
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID workflowGroupId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of("id", userId, "username", "demo-user", "email", "demo-user@local.invalid"));
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        """,
        Map.of("id", workflowGroupId, "code", "GROUP_ALPHA", "name", "Group Alpha"));
    jdbcTemplate.update(
        """
        INSERT INTO user_workflow_groups (user_id, workflow_group_id, created_at)
        VALUES (:userId, :workflowGroupId, now())
        """,
        Map.of("userId", userId, "workflowGroupId", workflowGroupId));
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :userId, :businessClientId, now())
        """,
        Map.of(
            "id",
            UUID.fromString("77777777-7777-7777-7777-777777777777"),
            "userId",
            userId,
            "businessClientId",
            "CLIENT_A"));

    mockMvc
        .perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value("11111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$.username").value("11111111-1111-1111-1111-111111111111"))
        .andExpect(jsonPath("$.permissions[0]").value("task.complete"))
        .andExpect(jsonPath("$.roles").isArray())
        .andExpect(jsonPath("$.workflowGroupIds[0]").value("GROUP_ALPHA"))
        .andExpect(jsonPath("$.clientScopeIds[0]").value("CLIENT_A"));
  }
}
