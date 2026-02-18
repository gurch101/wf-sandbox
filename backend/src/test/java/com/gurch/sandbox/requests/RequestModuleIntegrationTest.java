package com.gurch.sandbox.requests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class RequestModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private ExternalTaskService externalTaskService;
  @Autowired private RequestTypeRepository requestTypeRepository;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
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
        .andExpect(jsonPath("$.errors[0].name").value("amount"))
        .andExpect(jsonPath("$.errors[0].code").value("Positive"));

    assertThat(requestRepository.count()).isZero();
  }

  @Test
  void shouldCreateMoneyInRequestWithTypedTransferPayload() throws Exception {
    createType("money-in", "Money In", "money-in", "requestTypeV1Process");

    Long requestId =
        createRequest(
            "money-in",
            Map.of("fromAccount", "ACCOUNT-A", "toAccount", "ACCOUNT-B", "amount", 150.75));

    RequestEntity request = requestRepository.findById(requestId).orElseThrow();
    assertThat(request.getRequestTypeKey()).isEqualTo("money-in");
    assertThat(request.getRequestTypeVersion()).isEqualTo(1);
    assertThat(request.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
  }

  @Test
  void shouldRejectMoneyInRequestWhenAccountsMatch() throws Exception {
    createType("money-in", "Money In", "money-in", "requestTypeV1Process");

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest(
                            "money-in",
                            objectMapper.readTree(
                                "{\"fromAccount\":\"ACCOUNT-A\",\"toAccount\":\"ACCOUNT-A\",\"amount\":10}")))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].name").value("payload"))
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
        .andExpect(jsonPath("$.errors[0].name").value("amount"))
        .andExpect(jsonPath("$.errors[0].code").value("NotNull"));

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
  void detailsShouldIncludePayload() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    Long requestId = createRequest("loan", Map.of("amount", 77));

    mockMvc
        .perform(get("/api/requests/{id}", requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.payload.amount").value(77))
        .andExpect(jsonPath("$.requestTypeKey").value("loan"))
        .andExpect(jsonPath("$.requestTypeVersion").value(1));
  }

  @Test
  void searchShouldOmitPayload() throws Exception {
    createType("loan", "Loan", "amount-positive", "requestTypeV1Process");
    createRequest("loan", Map.of("amount", 77));

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
