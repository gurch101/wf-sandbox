package com.gurch.sandbox.requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requests.dto.RequestDtos;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskStatus;
import com.gurch.sandbox.requests.tasks.internal.models.RequestTaskEntity;
import com.gurch.sandbox.requesttypes.dto.RequestTypeDtos;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RequestModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
  }

  @Test
  void shouldRejectCreateWithInvalidRequestTypeKey() throws Exception {
    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new RequestDtos.CreateRequest("missing-type"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REQUEST_TYPE_NOT_FOUND"));

    assertThat(requestRepository.count()).isZero();
  }

  @Test
  void shouldRejectDraftCreateWithInvalidRequestTypeKey() throws Exception {
    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(new RequestDtos.CreateRequest("missing-type"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REQUEST_TYPE_NOT_FOUND"));

    assertThat(requestRepository.count()).isZero();
  }

  @Test
  void detailsShouldNotIncludePayload() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");
    Long requestId = createRequest("loan");

    mockMvc
        .perform(get("/api/requests/{id}", requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload").doesNotExist())
        .andExpect(jsonPath("$.requestTypeKey").value("loan"))
        .andExpect(jsonPath("$.requestTypeVersion").value(1));
  }

  @Test
  void searchShouldOmitPayload() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");
    createRequest("loan");

    mockMvc
        .perform(get("/api/requests/search"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].payload").doesNotExist());
  }

  @Test
  void shouldSearchByListOfRequestTypeKeys() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "requestTypeV2Process");

    createRequest("loan");
    createRequest("mortgage");

    mockMvc
        .perform(
            get("/api/requests/search")
                .param("requestTypeKeys", "loan")
                .param("requestTypeKeys", "mortgage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void shouldCompleteUserTaskEndpoint() throws Exception {
    createType("manual", "Manual", "simpleUserTaskProcess");
    Long requestId = createRequest("manual");

    RequestTaskEntity task = requestTaskRepository.findByRequestId(requestId).getFirst();

    mockMvc
        .perform(
            post("/api/requests/{requestId}/tasks/{taskId}/complete", requestId, task.getId())
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CompleteTaskRequest(TaskAction.APPROVED, "approved"))))
        .andExpect(status().isNoContent());

    assertTaskStatusEventually(task.getId(), RequestTaskStatus.COMPLETED);
  }

  @Test
  void shouldPopulateCorrelationIdFromIdempotencyKeyForAuditEvents() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");
    String idempotencyKey = UUID.randomUUID().toString();

    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RequestDtos.CreateRequest("loan"))))
            .andExpect(status().isCreated())
            .andReturn();

    Long requestId =
        objectMapper
            .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
            .getId();
    String correlationId =
        jdbcTemplate.queryForObject(
            """
            SELECT correlation_id
            FROM audit_log_events
            WHERE resource_type = 'requests'
              AND resource_id = ?
            ORDER BY created_at DESC, id DESC
            LIMIT 1
            """,
            String.class,
            requestId.toString());

    assertThat(correlationId).isEqualTo(idempotencyKey);
  }

  @Test
  void shouldExposeRequestActivityEvents() throws Exception {
    createType("manual", "Manual", "simpleUserTaskProcess");
    Long requestId = createRequest("manual");

    RequestTaskEntity task = requestTaskRepository.findByRequestId(requestId).getFirst();
    mockMvc
        .perform(
            post("/api/requests/{requestId}/tasks/{taskId}/complete", requestId, task.getId())
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CompleteTaskRequest(TaskAction.APPROVED, "approved"))))
        .andExpect(status().isNoContent());
    assertTaskStatusEventually(task.getId(), RequestTaskStatus.COMPLETED);

    MvcResult activityResult =
        mockMvc
            .perform(get("/api/requests/{id}/activity", requestId))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(activityResult.getResponse().getContentAsString());
    List<String> eventTypes = body.path("items").findValuesAsText("eventType").stream().toList();

    assertThat(eventTypes).contains("TASK_COMPLETED", "STATUS_CHANGED");
    assertThat(eventTypes).doesNotContain("TASK_ASSIGNED");
  }

  @Test
  void shouldReturnNotFoundWhenDeletingMissingRequest() throws Exception {
    mockMvc
        .perform(
            delete("/api/requests/{id}", Long.MAX_VALUE)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
  }

  @Test
  void shouldRejectDraftUpdateWithStaleVersion() throws Exception {
    createType("loan", "Loan", "requestTypeV1Process");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RequestDtos.CreateRequest("loan"))))
            .andExpect(status().isCreated())
            .andReturn();

    Long requestId =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    mockMvc
        .perform(
            put("/api/requests/{id}", requestId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequestDtos.UpdateDraftRequest(0L))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            put("/api/requests/{id}", requestId)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RequestDtos.UpdateDraftRequest(0L))))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.title").value("Optimistic Locking Failure"));
  }

  @Test
  void shouldFilterRequestActivityByEventTypeAndDateRange() throws Exception {
    createType("manual", "Manual", "simpleUserTaskProcess");
    Long requestId = createRequest("manual");

    RequestTaskEntity task = requestTaskRepository.findByRequestId(requestId).getFirst();
    mockMvc
        .perform(
            post("/api/requests/{requestId}/tasks/{taskId}/complete", requestId, task.getId())
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CompleteTaskRequest(TaskAction.APPROVED, "approved"))))
        .andExpect(status().isNoContent());
    assertTaskStatusEventually(task.getId(), RequestTaskStatus.COMPLETED);

    JsonNode allEvents =
        objectMapper.readTree(
            mockMvc
                .perform(get("/api/requests/{id}/activity", requestId))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    JsonNode completedEvent = null;
    for (JsonNode item : allEvents.path("items")) {
      if ("TASK_COMPLETED".equals(item.path("eventType").asText())) {
        completedEvent = item;
        break;
      }
    }
    assertThat(completedEvent).isNotNull();
    Instant completedAt = Instant.parse(completedEvent.path("createdAt").asText());

    JsonNode filtered =
        objectMapper.readTree(
            mockMvc
                .perform(
                    get("/api/requests/{id}/activity", requestId)
                        .param("eventTypes", "TASK_COMPLETED")
                        .param("createdAtFrom", completedAt.minusMillis(1).toString())
                        .param("createdAtTo", completedAt.plusMillis(1).toString()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

    List<String> filteredTypes =
        filtered.path("items").findValuesAsText("eventType").stream().toList();
    assertThat(filteredTypes).isNotEmpty();
    assertThat(filteredTypes).allMatch("TASK_COMPLETED"::equals);
  }

  private void createType(String typeKey, String name, String processDefinitionKey)
      throws Exception {
    mockMvc
        .perform(
            post("/api/internal/request-types")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.CreateTypeRequest(
                            typeKey, name, "desc", processDefinitionKey))))
        .andExpect(status().isCreated());
  }

  private Long createRequest(String typeKey) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(new RequestDtos.CreateRequest(typeKey))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }

  private void assertTaskStatusEventually(Long taskId, RequestTaskStatus expected)
      throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    while (Instant.now().isBefore(deadline)) {
      RequestTaskEntity current = requestTaskRepository.findById(taskId).orElseThrow();
      if (current.getStatus() == expected) {
        return;
      }
      Thread.sleep(100);
    }
    RequestTaskEntity latest = requestTaskRepository.findById(taskId).orElseThrow();
    assertThat(latest.getStatus()).isEqualTo(expected);
  }
}
