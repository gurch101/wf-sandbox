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
import com.gurch.sandbox.requests.internal.RequestEntity;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requests.internal.RequestTaskEntity;
import com.gurch.sandbox.requests.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.internal.RequestTaskStatus;
import com.gurch.sandbox.requesttypes.RequestTypeDtos;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.finos.fluxnova.bpm.engine.ExternalTaskService;
import org.finos.fluxnova.bpm.engine.externaltask.LockedExternalTask;
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
  @Autowired private ExternalTaskService externalTaskService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM request_types");
  }

  @Test
  void shouldAutoResolveLatestActiveVersionAndPinExistingRequests() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    Long firstRequestId = createRequest("loan", Map.of("amount", 100));
    RequestEntity first = requestRepository.findById(firstRequestId).orElseThrow();
    assertThat(first.getRequestTypeVersion()).isEqualTo(1);

    updateType("loan", "Loan V2", "amount-positive", "requestTypeV2Process");

    Long secondRequestId = createRequest("loan", Map.of("amount", 250));
    RequestEntity second = requestRepository.findById(secondRequestId).orElseThrow();
    assertThat(second.getRequestTypeVersion()).isEqualTo(2);

    RequestEntity firstAfterUpdate = requestRepository.findById(firstRequestId).orElseThrow();
    assertThat(firstAfterUpdate.getRequestTypeVersion()).isEqualTo(1);
  }

  @Test
  void shouldIgnoreClientProvidedVersionAndUseLatestActive() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    String rawPayload =
        """
        {
          "requestTypeKey": "loan",
          "requestTypeVersion": 999,
          "payload": { "amount": 42 }
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(rawPayload))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
            .getId();
    RequestEntity saved = requestRepository.findById(id).orElseThrow();
    assertThat(saved.getRequestTypeVersion()).isEqualTo(1);
  }

  @Test
  void shouldKeepDraftUnvalidatedUntilSubmit() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RequestDtos.CreateRequest(
                                "loan", objectMapper.readTree("{\"amount\":0}")))))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();
    RequestEntity draft = requestRepository.findById(id).orElseThrow();
    assertThat(draft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(draft.getRequestTypeVersion()).isNull();
    assertThat(draft.getProcessInstanceId()).isNull();

    mockMvc
        .perform(
            post("/api/requests/{id}/submit", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_REQUEST_PAYLOAD"));

    RequestEntity stillDraft = requestRepository.findById(id).orElseThrow();
    assertThat(stillDraft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(stillDraft.getProcessInstanceId()).isNull();
  }

  @Test
  void shouldUpdateAndSubmitDraft() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RequestDtos.CreateRequest(
                                "loan", objectMapper.readTree("{\"amount\":0}")))))
            .andExpect(status().isCreated())
            .andReturn();
    Long id =
        objectMapper
            .readValue(createResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    mockMvc
        .perform(
            put("/api/requests/{id}", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.UpdateDraftRequest(
                            objectMapper.readTree("{\"amount\":25}"), 0L))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));

    mockMvc
        .perform(
            post("/api/requests/{id}/submit", id)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
  }

  @Test
  void shouldFailSyncValidationWithoutStartingWorkflow() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest(
                            "loan", objectMapper.readTree("{\"amount\":0}")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_REQUEST_PAYLOAD"));

    assertThat(requestRepository.count()).isZero();
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
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest(
                            "missing-type", objectMapper.readTree("{\"amount\":25}")))))
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
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest(
                            "missing-type", objectMapper.readTree("{\"amount\":25}")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REQUEST_TYPE_NOT_FOUND"));

    assertThat(requestRepository.count()).isZero();
  }

  @Test
  void shouldFailSyncValidationForMissingAmountField() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest("loan", objectMapper.readTree("{}")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_REQUEST_PAYLOAD"));

    assertThat(requestRepository.count()).isZero();
  }

  @Test
  void shouldSetRejectedWhenAsyncValidationFails() throws Exception {
    createType("ops", "Ops", "noop", "requestTypeV1Process");
    Long requestId = createRequest("ops", Map.of("value", "x"));

    LockedExternalTask validationTask = fetchExternalTask("request-async-validation", requestId);
    externalTaskService.complete(
        validationTask.getId(),
        validationTask.getWorkerId(),
        Map.of("asyncValidationPassed", false));

    assertStatusEventually(requestId, RequestStatus.REJECTED);
  }

  @Test
  void shouldProceedToBorTaskWhenAsyncValidationPasses() throws Exception {
    createType("ops", "Ops", "noop", "requestTypeV1Process");
    Long requestId = createRequest("ops", Map.of("value", "x"));

    LockedExternalTask validationTask = fetchExternalTask("request-async-validation", requestId);
    externalTaskService.complete(
        validationTask.getId(),
        validationTask.getWorkerId(),
        Map.of("asyncValidationPassed", true));

    LockedExternalTask borTask = fetchExternalTask("request-bor", requestId);
    assertThat(borTask).isNotNull();
  }

  @Test
  void shouldCompleteRequestWhenBorTaskSucceeds() throws Exception {
    createType("ops", "Ops", "noop", "requestTypeV1Process");
    Long requestId = createRequest("ops", Map.of("value", "x"));

    LockedExternalTask validationTask = fetchExternalTask("request-async-validation", requestId);
    externalTaskService.complete(
        validationTask.getId(),
        validationTask.getWorkerId(),
        Map.of("asyncValidationPassed", true));

    LockedExternalTask borTask = fetchExternalTask("request-bor", requestId);
    externalTaskService.complete(borTask.getId(), borTask.getWorkerId());

    assertStatusEventually(requestId, RequestStatus.COMPLETED);
  }

  @Test
  void shouldKeepRequestInProgressWhenExternalTaskIncidentOccurs() throws Exception {
    createType("ops", "Ops", "noop", "requestTypeV1Process");
    Long requestId = createRequest("ops", Map.of("value", "x"));

    LockedExternalTask validationTask = fetchExternalTask("request-async-validation", requestId);
    externalTaskService.complete(
        validationTask.getId(),
        validationTask.getWorkerId(),
        Map.of("asyncValidationPassed", true));

    LockedExternalTask borTask = fetchExternalTask("request-bor", requestId);
    externalTaskService.handleFailure(
        borTask.getId(), borTask.getWorkerId(), "downstream unavailable", "retry exhausted", 0, 0L);

    assertStatusEventually(requestId, RequestStatus.IN_PROGRESS);
    assertThat(
            externalTaskService
                .createExternalTaskQuery()
                .processInstanceId(borTask.getProcessInstanceId())
                .noRetriesLeft()
                .count())
        .isGreaterThan(0);
  }

  @Test
  void detailsShouldIncludePayloadAndSearchShouldOmitPayload() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    Long requestId = createRequest("loan", Map.of("amount", 77));

    mockMvc
        .perform(get("/api/requests/{id}", requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.amount").value(77))
        .andExpect(jsonPath("$.requestTypeKey").value("loan"))
        .andExpect(jsonPath("$.requestTypeVersion").value(1));

    mockMvc
        .perform(get("/api/requests/search"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests[0].payload").doesNotExist());
  }

  @Test
  void shouldSearchByListOfRequestTypeKeys() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "noop", "requestTypeV2Process");

    createRequest("loan", Map.of("amount", 12));
    createRequest("mortgage", Map.of("value", "x"));

    mockMvc
        .perform(
            get("/api/requests/search")
                .param("requestTypeKeys", "loan")
                .param("requestTypeKeys", "mortgage"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requests.length()").value(2));
  }

  @Test
  void shouldCompleteUserTaskEndpoint() throws Exception {
    createType("manual", "Manual", "noop", "simpleUserTaskProcess");
    Long requestId = createRequest("manual", Map.of("value", "x"));

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
  void shouldSearchRequestTypes() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createType("mortgage", "Mortgage", "noop", "requestTypeV2Process");

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "loa"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].typeKey").value("loan"))
        .andExpect(jsonPath("$[0].activeVersion").value(1));
  }

  @Test
  void shouldDeleteUnusedRequestTypeAndRejectDeleteWhenInUse() throws Exception {
    createType("unused", "Unused", "noop", "requestTypeV2Process");
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createRequest("loan", Map.of("amount", 10));

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "unused")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/internal/request-types/search").param("typeKeyContains", "unused"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));

    mockMvc
        .perform(
            delete("/api/internal/request-types/{typeKey}", "loan")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REQUEST_TYPE_IN_USE"));
  }

  @Test
  void shouldRejectCreateTypeWithInvalidProcessDefinitionKey() throws Exception {
    mockMvc
        .perform(
            post("/api/internal/request-types")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.CreateTypeRequest(
                            "bad", "Bad", "desc", "noop", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  @Test
  void shouldRejectUpdateTypeWithInvalidProcessDefinitionKey() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");

    mockMvc
        .perform(
            put("/api/internal/request-types/{typeKey}", "loan")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.ChangeTypeRequest(
                            "Loan", "desc", "amount-positive", "missing-process-definition-key"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("INVALID_PROCESS_DEFINITION_KEY"));
  }

  @Test
  void shouldSearchAndValidateWorkflowDefinitions() throws Exception {
    mockMvc
        .perform(
            get("/api/internal/workflows/process-definitions/search")
                .param("processDefinitionKeyContains", "requestTypeV1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].key").value("requestTypeV1Process"));

    mockMvc
        .perform(
            get("/api/internal/workflows/process-definitions/{key}/exists", "requestTypeV1Process"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exists").value(true));

    mockMvc
        .perform(get("/api/internal/workflows/process-definitions/{key}/exists", "missingKey"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.exists").value(false));
  }

  private void createType(
      String typeKey, String name, String payloadHandlerId, String processDefinitionKey)
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
                            typeKey, name, "desc", payloadHandlerId, processDefinitionKey))))
        .andExpect(status().isCreated());
  }

  private void updateType(
      String typeKey, String name, String payloadHandlerId, String processDefinitionKey)
      throws Exception {
    mockMvc
        .perform(
            put("/api/internal/request-types/{typeKey}", typeKey)
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestTypeDtos.ChangeTypeRequest(
                            name, "desc", payloadHandlerId, processDefinitionKey))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
  }

  private Long createRequest(String typeKey, Map<String, Object> payload) throws Exception {
    JsonNode payloadNode = objectMapper.valueToTree(payload);
    MvcResult result =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new RequestDtos.CreateRequest(typeKey, payloadNode))))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readValue(result.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }

  private LockedExternalTask fetchExternalTask(String topic, Long requestId) {
    List<LockedExternalTask> tasks =
        externalTaskService
            .fetchAndLock()
            .workerId("it-worker")
            .maxTasks(1)
            .subscribe()
            .topic(topic, 60_000L)
            .businessKey(requestId.toString())
            .execute();

    assertThat(tasks).hasSize(1);
    return tasks.getFirst();
  }

  private void assertStatusEventually(Long requestId, RequestStatus expected) throws Exception {
    Instant deadline = Instant.now().plus(Duration.ofSeconds(5));
    while (Instant.now().isBefore(deadline)) {
      RequestEntity current = requestRepository.findById(requestId).orElseThrow();
      if (current.getStatus() == expected) {
        return;
      }
      Thread.sleep(100);
    }
    RequestEntity latest = requestRepository.findById(requestId).orElseThrow();
    assertThat(latest.getStatus()).isEqualTo(expected);
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
