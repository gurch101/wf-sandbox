package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.dto.CreateRequestCommand;
import com.gurch.sandbox.requests.dto.RequestResponse;
import com.gurch.sandbox.requests.dto.RequestSearchCriteria;
import com.gurch.sandbox.requests.dto.RequestSearchResponse;
import com.gurch.sandbox.requests.internal.models.RequestEntity;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskRepository;
import com.gurch.sandbox.requests.tasks.internal.RequestTaskStatus;
import com.gurch.sandbox.requests.tasks.internal.models.RequestTaskEntity;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.dto.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import com.gurch.sandbox.web.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class DefaultRequestServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestApi requestApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();

    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .processDefinitionKey("requestTypeV1Process")
            .build());
  }

  @AfterEach
  void tearDown() {
    jdbcTemplate.update("DELETE FROM audit_log_events");
  }

  @Test
  void shouldCreateSubmittedRequestWithResolvedTypeVersion() {
    var created =
        requestApi.createAndSubmit(CreateRequestCommand.builder().requestTypeKey("loan").build());

    assertThat(created.getRequestTypeKey()).isEqualTo("loan");
    assertThat(created.getRequestTypeVersion()).isEqualTo(1);

    RequestEntity saved = requestRepository.findById(created.getId()).orElseThrow();
    assertThat(saved.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    assertThat(saved.getProcessInstanceId()).isNotBlank();
  }

  @Test
  void shouldSearchByRequestTypeKeys() {
    requestApi.createAndSubmit(CreateRequestCommand.builder().requestTypeKey("loan").build());

    PagedResponse<RequestSearchResponse> results =
        requestApi.search(RequestSearchCriteria.builder().requestTypeKeys(List.of("loan")).build());

    assertThat(results.items()).hasSize(1);
    assertThat(results.items().getFirst().requestTypeKey()).isEqualTo("loan");
    assertThat(results.totalElements()).isEqualTo(1);
  }

  @Test
  void shouldAutoResolveLatestActiveVersionAndPinExistingRequests() {
    RequestResponse first =
        requestApi.createAndSubmit(CreateRequestCommand.builder().requestTypeKey("loan").build());

    requestTypeApi.changeType(
        "loan",
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan V2")
            .description("desc")
            .processDefinitionKey("requestTypeV2Process")
            .build());

    RequestResponse second =
        requestApi.createAndSubmit(CreateRequestCommand.builder().requestTypeKey("loan").build());

    assertThat(requestRepository.findById(first.getId()).orElseThrow().getRequestTypeVersion())
        .isEqualTo(1);
    assertThat(requestRepository.findById(second.getId()).orElseThrow().getRequestTypeVersion())
        .isEqualTo(2);
  }

  @Test
  void shouldKeepDraftUnvalidatedUntilSubmit() {
    Long draftId =
        requestApi.createDraft(CreateRequestCommand.builder().requestTypeKey("loan").build());

    RequestEntity draft = requestRepository.findById(draftId).orElseThrow();
    assertThat(draft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(draft.getRequestTypeVersion()).isEqualTo(1);
    assertThat(draft.getProcessInstanceId()).isNull();
    assertThat(auditActionsFor("requests", draftId.toString())).containsExactly("CREATE");
    assertThat(latestAuditActorForRequest(draftId)).isEqualTo(1);

    RequestEntity stillDraft = requestRepository.findById(draftId).orElseThrow();
    assertThat(stillDraft.getStatus()).isEqualTo(RequestStatus.DRAFT);
    assertThat(stillDraft.getRequestTypeVersion()).isEqualTo(1);
    assertThat(stillDraft.getProcessInstanceId()).isNull();
  }

  @Test
  void shouldSubmitDraftWithVersionCapturedAtDraftCreationTime() {
    Long draftId =
        requestApi.createDraft(CreateRequestCommand.builder().requestTypeKey("loan").build());
    assertThat(requestRepository.findById(draftId).orElseThrow().getRequestTypeVersion())
        .isEqualTo(1);

    requestTypeApi.changeType(
        "loan",
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan V2")
            .description("desc")
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
        requestApi.createDraft(CreateRequestCommand.builder().requestTypeKey("loan").build());

    requestApi.updateDraft(draftId, 0L);
    Map<String, Object> updateAuditRow = updateAuditDiffRow(draftId);
    assertThat((String) updateAuditRow.get("before_state"))
        .contains("\"updatedAt\"")
        .doesNotContain("\"status\":\"DRAFT\"");
    assertThat((String) updateAuditRow.get("after_state"))
        .contains("\"updatedAt\"")
        .contains("\"version\":")
        .doesNotContain("\"status\":\"DRAFT\"");

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
            .processDefinitionKey("simpleUserTaskProcess")
            .build());

    var created =
        requestApi.createAndSubmit(CreateRequestCommand.builder().requestTypeKey("manual").build());

    RequestTaskEntity task = requestTaskRepository.findByRequestId(created.getId()).getFirst();
    requestApi.completeTask(created.getId(), task.getId(), TaskAction.APPROVED, "approved");

    RequestTaskEntity completedTask = requestTaskRepository.findById(task.getId()).orElseThrow();
    assertThat(completedTask.getStatus()).isEqualTo(RequestTaskStatus.COMPLETED);
    assertThat(completedTask.getAction()).isEqualTo(TaskAction.APPROVED.name());
  }

  @Test
  void shouldDeleteDraft() {
    Long draftId =
        requestApi.createDraft(CreateRequestCommand.builder().requestTypeKey("loan").build());

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
    return jdbcTemplate.queryForObject(
        """
        SELECT actor_user_id
        FROM audit_log_events
        WHERE resource_type = 'requests'
          AND resource_id = ?
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """,
        Integer.class,
        requestId.toString());
  }

  private Map<String, Object> updateAuditDiffRow(Long requestId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT before_state::text AS before_state, after_state::text AS after_state
        FROM audit_log_events
        WHERE resource_type = 'requests'
          AND resource_id = ?
          AND action = 'UPDATE'
        ORDER BY created_at DESC, id DESC
        LIMIT 1
        """,
        requestId.toString());
  }
}
