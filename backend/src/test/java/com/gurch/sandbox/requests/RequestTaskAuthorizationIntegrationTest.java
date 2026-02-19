package com.gurch.sandbox.requests;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.requests.internal.RequestEntity;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requests.internal.RequestTaskEntity;
import com.gurch.sandbox.requests.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.internal.RequestTaskStatus;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

class RequestTaskAuthorizationIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private RequestApi requestApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private RequestTypeRepository requestTypeRepository;

  @BeforeEach
  void setUp() {
    requestTaskRepository.deleteAll();
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("simpleUserTaskProcess")
            .build());
  }

  @Test
  void shouldReturnForbiddenForTaskAssigneeSearchWithoutTaskListPermission() throws Exception {
    RequestResponse created = createSubmittedRequest("Task Filter Permission Gate");
    requestTaskRepository.save(
        RequestTaskEntity.builder()
            .requestId(created.getId())
            .processInstanceId("pi-task-list-authz")
            .taskId("task-list-authz")
            .name("Task List Authorization")
            .status(RequestTaskStatus.ACTIVE)
            .assignee("demo")
            .build());

    mockMvc
        .perform(
            get("/api/requests/search")
                .param("taskAssignee", "demo")
                .with(user("search-user").authorities(new SimpleGrantedAuthority("request.read"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowTaskAssigneeSearchWithTaskListPermission() throws Exception {
    RequestResponse created = createSubmittedRequest("Task Filter Permission Allowed");
    requestTaskRepository.save(
        RequestTaskEntity.builder()
            .requestId(created.getId())
            .processInstanceId("pi-task-list-allowed")
            .taskId("task-list-allowed")
            .name("Task List Allowed")
            .status(RequestTaskStatus.ACTIVE)
            .assignee("demo")
            .build());

    mockMvc
        .perform(
            get("/api/requests/search")
                .param("taskAssignee", "demo")
                .with(
                    user("search-user")
                        .authorities(
                            new SimpleGrantedAuthority("request.read"),
                            new SimpleGrantedAuthority("task.list"))))
        .andExpect(status().isOk());
  }

  @Test
  void shouldReturnForbiddenWhenClaimTaskWithoutTaskClaimPermission() throws Exception {
    RequestResponse created = createSubmittedRequest("Claim Permission Gate");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();

    mockMvc
        .perform(
            put("/api/tasks/{taskId}/claim", taskProjection.getId())
                .with(user("no-claim").authorities(new SimpleGrantedAuthority("task.list")))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowClaimTaskWhenPermissionGroupAndScopeMatch() throws Exception {
    RequestResponse created = createSubmittedRequest("Claim Allowed");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();
    RequestEntity request = requestRepository.findById(created.getId()).orElseThrow();
    String workflowGroupCode = "WF_TASK_CLAIM";
    jdbcTemplate.update(
        """
        UPDATE requests
        SET workflow_group_code = :workflowGroupCode, business_client_id = :businessClientId
        WHERE id = :id
        """,
        Map.of(
            "workflowGroupCode",
            workflowGroupCode,
            "businessClientId",
            "CLIENT_A",
            "id",
            request.getId()));

    UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
    UUID workflowGroupId = UUID.fromString("11112222-3333-4444-5555-666677778888");
    insertUser(userId, "claim-user", "claim-user@local.invalid");
    insertWorkflowGroup(workflowGroupId, workflowGroupCode, "Claimers");
    jdbcTemplate.update(
        """
        INSERT INTO user_workflow_groups (user_id, workflow_group_id, created_at)
        VALUES (:userId, :workflowGroupId, now())
        ON CONFLICT (user_id, workflow_group_id) DO NOTHING
        """,
        Map.of("userId", userId, "workflowGroupId", workflowGroupId));
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, now())
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "principalUserId", userId, "businessClientId", "CLIENT_A"));
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, now())
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "principalUserId", userId, "businessClientId", "CLIENT_A"));

    mockMvc
        .perform(
            put("/api/tasks/{taskId}/claim", taskProjection.getId())
                .with(user(userId.toString()).authorities(new SimpleGrantedAuthority("task.claim")))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            get("/api/tasks")
                .param("requestId", created.getId().toString())
                .with(user(userId.toString()).authorities(new SimpleGrantedAuthority("task.list"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].assignee").value(userId.toString()));
  }

  @Test
  void shouldReturnForbiddenWhenAssignTaskOutsideClientScope() throws Exception {
    RequestResponse created = createSubmittedRequest("Assign Scope Gate");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();
    RequestEntity request = requestRepository.findById(created.getId()).orElseThrow();
    String workflowGroupCode = "WF_TASK_ASSIGN";
    jdbcTemplate.update(
        """
        UPDATE requests
        SET workflow_group_code = :workflowGroupCode, business_client_id = :businessClientId
        WHERE id = :id
        """,
        Map.of(
            "workflowGroupCode",
            workflowGroupCode,
            "businessClientId",
            "CLIENT_A",
            "id",
            request.getId()));

    UUID userId = UUID.fromString("12121212-3434-5656-7878-909090909090");
    UUID workflowGroupId = UUID.fromString("01010101-0202-0303-0404-050505050505");
    insertUser(userId, "assign-user", "assign-user@local.invalid");
    insertWorkflowGroup(workflowGroupId, workflowGroupCode, "Assigners");
    jdbcTemplate.update(
        """
        INSERT INTO user_workflow_groups (user_id, workflow_group_id, created_at)
        VALUES (:userId, :workflowGroupId, now())
        ON CONFLICT (user_id, workflow_group_id) DO NOTHING
        """,
        Map.of("userId", userId, "workflowGroupId", workflowGroupId));
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, now())
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "principalUserId", userId, "businessClientId", "CLIENT_B"));

    mockMvc
        .perform(
            put("/api/tasks/{taskId}/assign", taskProjection.getId())
                .with(
                    user(userId.toString()).authorities(new SimpleGrantedAuthority("task.assign")))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new TaskDtos.AssignTaskRequest("demo"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldReturnForbiddenWhenUserLacksWorkflowGroupMembership() throws Exception {
    RequestResponse created = createSubmittedRequest("Group Gate Request");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();
    RequestEntity request = requestRepository.findById(created.getId()).orElseThrow();
    String groupCode = "WF_APPROVERS";
    jdbcTemplate.update(
        "UPDATE requests SET workflow_group_code = :groupCode WHERE id = :id",
        Map.of("groupCode", groupCode, "id", request.getId()));
    UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", userId, "username", userId.toString(), "email", "u1@local.invalid"));
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        ON CONFLICT (code) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "code", groupCode, "name", "Approvers"));

    mockMvc
        .perform(
            post(
                    "/api/requests/{requestId}/tasks/{taskId}/complete",
                    request.getId(),
                    taskProjection.getId())
                .with(
                    user(userId.toString())
                        .authorities(new SimpleGrantedAuthority("task.complete")))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CompleteTaskRequest(TaskAction.APPROVED, "ok"))))
        .andExpect(status().isForbidden());
  }

  @Test
  void shouldAllowCompleteWhenUserHasPermissionAndGroupMembership() throws Exception {
    RequestResponse created = createSubmittedRequest("Allowed Group Request");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();
    RequestEntity request = requestRepository.findById(created.getId()).orElseThrow();
    String groupCode = "WF_EXECUTORS";
    jdbcTemplate.update(
        "UPDATE requests SET workflow_group_code = :groupCode WHERE id = :id",
        Map.of("groupCode", groupCode, "id", request.getId()));
    UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", userId, "username", userId.toString(), "email", "u2@local.invalid"));
    UUID workflowGroupId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        ON CONFLICT (code) DO NOTHING
        """,
        Map.of("id", workflowGroupId, "code", groupCode, "name", "Executors"));
    jdbcTemplate.update(
        """
        INSERT INTO user_workflow_groups (user_id, workflow_group_id, created_at)
        VALUES (:userId, :workflowGroupId, now())
        ON CONFLICT (user_id, workflow_group_id) DO NOTHING
        """,
        Map.of("userId", userId, "workflowGroupId", workflowGroupId));
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, now())
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "principalUserId", userId, "businessClientId", "CLIENT_A"));

    mockMvc
        .perform(
            post(
                    "/api/requests/{requestId}/tasks/{taskId}/complete",
                    request.getId(),
                    taskProjection.getId())
                .with(
                    user(userId.toString())
                        .authorities(new SimpleGrantedAuthority("task.complete")))
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CompleteTaskRequest(TaskAction.APPROVED, "approved"))))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldListTasksFilteredByClientScope() throws Exception {
    RequestResponse inScope = createSubmittedRequest("Task Scope A");
    RequestResponse outScope = createSubmittedRequest("Task Scope B");
    jdbcTemplate.update(
        "UPDATE requests SET business_client_id = :businessClientId WHERE id = :id",
        Map.of("businessClientId", "CLIENT_A", "id", inScope.getId()));
    jdbcTemplate.update(
        "UPDATE requests SET business_client_id = :businessClientId WHERE id = :id",
        Map.of("businessClientId", "CLIENT_B", "id", outScope.getId()));

    UUID userId = UUID.fromString("56565656-7878-9090-1111-222222222222");
    insertUser(userId, "task-list-user", "task-list-user@local.invalid");
    jdbcTemplate.update(
        """
        INSERT INTO principal_client_scopes (id, principal_user_id, business_client_id, created_at)
        VALUES (:id, :principalUserId, :businessClientId, now())
        ON CONFLICT (principal_user_id, business_client_id) DO NOTHING
        """,
        Map.of("id", UUID.randomUUID(), "principalUserId", userId, "businessClientId", "CLIENT_A"));

    mockMvc
        .perform(
            get("/api/tasks")
                .with(user(userId.toString()).authorities(new SimpleGrantedAuthority("task.list"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
  }

  private void insertUser(UUID userId, String username, String email) {
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of("id", userId, "username", username, "email", email));
  }

  private void insertWorkflowGroup(UUID workflowGroupId, String code, String name) {
    jdbcTemplate.update(
        """
        INSERT INTO workflow_groups (id, code, name, created_at, updated_at)
        VALUES (:id, :code, :name, now(), now())
        ON CONFLICT (code) DO NOTHING
        """,
        Map.of("id", workflowGroupId, "code", code, "name", name));
  }

  private RequestResponse createSubmittedRequest(String label) {
    return requestApi.createAndSubmit(
        CreateRequestCommand.builder()
            .requestTypeKey("loan")
            .payload(objectMapper.createObjectNode().put("amount", 10).put("label", label))
            .build());
  }
}
