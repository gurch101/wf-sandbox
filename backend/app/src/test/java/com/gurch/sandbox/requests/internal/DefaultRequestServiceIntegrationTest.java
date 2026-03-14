package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.audit.internal.AuditLogAction;
import com.gurch.sandbox.audit.internal.AuditLogEventEntity;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requests.CreateRequestCommand;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestSearchResponse;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskEntity;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskStatus;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DefaultRequestServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestApi requestApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();

    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV1Process")
            .build());
  }

  @AfterEach
  void tearDown() {
    auditLogEventRepository.deleteAll();
  }

  @Test
  void shouldCreateSubmittedRequestWithResolvedTypeVersion() {
    var created =
        requestApi.createAndSubmit(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 10)))
                .build());

    assertThat(created.getRequestTypeKey()).isEqualTo("loan");
    assertThat(created.getRequestTypeVersion()).isEqualTo(1);

    RequestEntity saved = requestRepository.findById(created.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    assertThat(saved.getProcessInstanceId()).isNotBlank();
  }

  @Test
  void shouldSearchByRequestTypeKeys() {
    requestApi.createAndSubmit(
        CreateRequestCommand.builder()
            .requestTypeKey("loan")
            .payload(objectMapper.valueToTree(Map.of("amount", 15)))
            .build());

    PagedResponse<RequestSearchResponse> results =
        requestApi.search(RequestSearchCriteria.builder().requestTypeKeys(List.of("loan")).build());

    assertThat(results.items()).hasSize(1);
    assertThat(results.items().getFirst().requestTypeKey()).isEqualTo("loan");
    assertThat(results.totalElements()).isEqualTo(1);
  }

  @Test
  void shouldAutoResolveLatestActiveVersionAndPinExistingRequests() {
    RequestResponse first =
        requestApi.createAndSubmit(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 100)))
                .build());

    requestTypeApi.changeType(
        "loan",
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan V2")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV2Process")
            .build());

    RequestResponse second =
        requestApi.createAndSubmit(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 250)))
                .build());

    assertThat(requestRepository.findById(first.getId()).orElseThrow().getRequestTypeVersion())
        .isEqualTo(1);
    assertThat(requestRepository.findById(second.getId()).orElseThrow().getRequestTypeVersion())
        .isEqualTo(2);
  }

  @Test
  void shouldKeepDraftUnvalidatedUntilSubmit() {
    Long draftId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 0)))
                .build());

    RequestEntity draft = requestRepository.findById(draftId).orElseThrow();
    assertThat(draft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(draft.getRequestTypeVersion()).isEqualTo(1);
    assertThat(draft.getProcessInstanceId()).isNull();
    assertThat(auditActionsFor("requests", draftId.toString())).containsExactly("CREATE");
    assertThat(latestAuditActorForRequest(draftId)).isEqualTo(1);

    assertThatThrownBy(() -> requestApi.submitDraft(draftId))
        .isInstanceOf(ValidationErrorException.class)
        .satisfies(
            throwable -> {
              ValidationErrorException exception = (ValidationErrorException) throwable;
              assertThat(exception.getErrors()).hasSize(1);
              assertThat(exception.getErrors().getFirst().name()).isEqualTo("amount");
              assertThat(exception.getErrors().getFirst().code()).isEqualTo("Positive");
            });

    RequestEntity stillDraft = requestRepository.findById(draftId).orElseThrow();
    assertThat(stillDraft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(stillDraft.getRequestTypeVersion()).isEqualTo(1);
    assertThat(stillDraft.getProcessInstanceId()).isNull();
  }

  @Test
  void shouldSubmitDraftWithVersionCapturedAtDraftCreationTime() {
    Long draftId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 50)))
                .build());
    assertThat(requestRepository.findById(draftId).orElseThrow().getRequestTypeVersion())
        .isEqualTo(1);

    requestTypeApi.changeType(
        "loan",
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan V2")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV2Process")
            .build());

    RequestResponse submitted = requestApi.submitDraft(draftId);
    assertThat(submitted.getRequestTypeVersion()).isEqualTo(1);
    assertThat(requestRepository.findById(draftId).orElseThrow().getRequestTypeVersion())
        .isEqualTo(1);
  }

  @Test
  void shouldUpdateAndSubmitDraft() throws Exception {
    Long draftId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 0)))
                .build());

    requestApi.updateDraft(draftId, objectMapper.valueToTree(Map.of("amount", 25)), 0L);
    Map<String, Object> updateAuditRow = updateAuditDiffRow(draftId);
    JsonNode beforeState = objectMapper.readTree((String) updateAuditRow.get("before_state"));
    JsonNode afterState = objectMapper.readTree((String) updateAuditRow.get("after_state"));
    assertThat(beforeState.path("payloadJson").path("amount").asInt()).isEqualTo(0);
    assertThat(afterState.path("payloadJson").path("amount").asInt()).isEqualTo(25);
    assertThat(beforeState.has("requestTypeKey")).isFalse();
    assertThat(afterState.has("requestTypeKey")).isFalse();

    RequestResponse submitted = requestApi.submitDraft(draftId);

    assertThat(submitted.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    assertThat(requestRepository.findById(draftId).orElseThrow().getStatus())
        .isEqualTo(RequestStatus.IN_PROGRESS);
  }

  @Test
  void shouldCompleteUserTask() {
    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("manual")
            .name("Manual")
            .description("desc")
            .payloadHandlerId("noop")
            .processDefinitionKey("simpleUserTaskProcess")
            .build());

    var created =
        requestApi.createAndSubmit(
            CreateRequestCommand.builder()
                .requestTypeKey("manual")
                .payload(objectMapper.valueToTree(Map.of("value", "x")))
                .build());

    RequestTaskEntity task = requestTaskRepository.findByRequestId(created.getId()).getFirst();
    requestApi.completeTask(created.getId(), task.getId(), TaskAction.APPROVED, "approved");

    RequestTaskEntity completedTask = requestTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(completedTask.getStatus()).isEqualTo(RequestTaskStatus.COMPLETED);
    assertThat(completedTask.getAction()).isEqualTo(TaskAction.APPROVED.name());
  }

  @Test
  void shouldDeleteDraft() {
    Long draftId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 42)))
                .build());

    requestApi.deleteById(draftId);

    assertThat(requestRepository.findById(draftId)).isEmpty();
    assertThat(auditActionsFor("requests", draftId.toString())).containsExactly("DELETE", "CREATE");
  }

  @Test
  void shouldFailDeleteForMissingRequest() {
    assertThatThrownBy(() -> requestApi.deleteById(Long.MAX_VALUE))
        .isInstanceOf(NotFoundException.class)
        .hasMessageContaining("Request not found with id");
  }

  private Integer latestAuditActorForRequest(Long requestId) {
    return StreamSupport.stream(auditLogEventRepository.findAll().spliterator(), false)
        .filter(event -> "requests".equals(event.getResourceType()))
        .filter(event -> requestId.toString().equals(event.getResourceId()))
        .sorted(
            Comparator.comparing(AuditLogEventEntity::getCreatedAt)
                .thenComparing(AuditLogEventEntity::getId)
                .reversed())
        .map(AuditLogEventEntity::getActorUserId)
        .findFirst()
        .orElseThrow();
  }

  private Map<String, Object> updateAuditDiffRow(Long requestId) {
    AuditLogEventEntity event =
        StreamSupport.stream(auditLogEventRepository.findAll().spliterator(), false)
            .filter(row -> "requests".equals(row.getResourceType()))
            .filter(row -> requestId.toString().equals(row.getResourceId()))
            .filter(row -> AuditLogAction.UPDATE.equals(row.getAction()))
            .sorted(
                Comparator.comparing(AuditLogEventEntity::getCreatedAt)
                    .thenComparing(AuditLogEventEntity::getId)
                    .reversed())
            .findFirst()
            .orElseThrow();
    return Map.of(
        "before_state",
        event.getBeforeState().toString(),
        "after_state",
        event.getAfterState().toString());
  }
}
