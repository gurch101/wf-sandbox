package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.TaskAction;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class DefaultRequestServiceIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestApi requestApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTaskRepository requestTaskRepository;

  @BeforeEach
  void setUp() {
    requestRepository.deleteAll();
  }

  @Test
  void shouldCreateProjectionTaskWhenSubmittedRequestStartsWorkflow() {
    RequestResponse created = requestApi.createAndSubmit("Service Layer Request");

    assertThat(created.getStatus()).isEqualTo(RequestStatus.IN_PROGRESS);
    RequestEntity saved = requestRepository.findById(created.getId()).orElseThrow();
    assertThat(saved.getProcessInstanceId()).isNotBlank();

    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();
    assertThat(taskProjection.getTaskId()).isNotBlank();
    assertThat(taskProjection.getName()).isEqualTo("Approve Request");
    assertThat(taskProjection.getStatus()).isEqualTo(RequestTaskStatus.ACTIVE);
    assertThat(taskProjection.getAssignee()).isNull();
  }

  @Test
  void shouldCompleteWorkflowAndProjectionTaskViaService() {
    RequestResponse created = requestApi.createAndSubmit("Service Completion");
    RequestTaskEntity taskProjection =
        requestTaskRepository.findByRequestId(created.getId()).getFirst();

    requestApi.completeTask(
        created.getId(), taskProjection.getId(), TaskAction.APPROVED, "approved");

    RequestEntity completed = requestRepository.findById(created.getId()).orElseThrow();
    assertThat(completed.getStatus()).isEqualTo(RequestStatus.COMPLETED);

    RequestTaskEntity completedTask =
        requestTaskRepository.findById(taskProjection.getId()).orElseThrow();
    assertThat(completedTask.getStatus()).isEqualTo(RequestTaskStatus.COMPLETED);
    assertThat(completedTask.getAction()).isEqualTo(TaskAction.APPROVED.name());
    assertThat(completedTask.getComment()).isEqualTo("approved");
  }

  @Test
  void shouldSearchByTaskAssigneeFromProjectionTable() {
    RequestEntity owner =
        requestRepository.save(
            RequestEntity.builder()
                .name("Searchable Task Owner")
                .status(RequestStatus.DRAFT)
                .build());
    requestRepository.save(
        RequestEntity.builder().name("No Task Yet").status(RequestStatus.DRAFT).build());
    requestTaskRepository.save(
        RequestTaskEntity.builder()
            .requestId(owner.getId())
            .processInstanceId("pi-search")
            .taskId("task-search")
            .name("Manual Task")
            .status(RequestTaskStatus.ACTIVE)
            .assignee("demo")
            .build());

    RequestSearchCriteria criteria =
        RequestSearchCriteria.builder().taskAssignees(List.of("missing-user", "demo")).build();
    assertThat(requestApi.search(criteria))
        .extracting(RequestResponse::getName)
        .containsExactly("Searchable Task Owner");
  }
}
