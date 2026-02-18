package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.requests.CreateRequestCommand;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestSearchResponse;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.TaskAction;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DefaultRequestServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestApi requestApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    jdbcTemplate.update("DELETE FROM request_types");

    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV1Process")
            .build());
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

    List<RequestSearchResponse> results =
        requestApi.search(RequestSearchCriteria.builder().requestTypeKeys(List.of("loan")).build());

    assertThat(results).hasSize(1);
    assertThat(results.getFirst().requestTypeKey()).isEqualTo("loan");
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
  void shouldUpdateAndSubmitDraft() {
    Long draftId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("loan")
                .payload(objectMapper.valueToTree(Map.of("amount", 0)))
                .build());

    requestApi.updateDraft(draftId, objectMapper.valueToTree(Map.of("amount", 25)), 0L);
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
    assertThat(completedTask.getComment()).isEqualTo("approved");
  }
}
