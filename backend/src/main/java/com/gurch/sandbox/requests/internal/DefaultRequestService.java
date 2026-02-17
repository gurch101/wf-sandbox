package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.QueryLoggingHelper;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestDraftErrorCode;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.RequestTaskResponse;
import com.gurch.sandbox.requests.TaskAction;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultRequestService implements RequestApi {

  private static final String REQUEST_PROCESS_KEY = "simpleUserTaskProcess";

  private final RequestRepository repository;
  private final RequestTaskRepository requestTaskRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final RuntimeService runtimeService;
  private final TaskService taskService;

  @Override
  public List<RequestResponse> findAll() {
    return repository.findAll().stream().map(entity -> toResponse(entity, false)).toList();
  }

  @Override
  public Optional<RequestResponse> findById(Long id) {
    return repository.findById(id).map(entity -> toResponse(entity, true));
  }

  @Override
  @Transactional
  public RequestResponse createDraft(String name) {
    RequestEntity saved =
        repository.save(RequestEntity.builder().name(name).status(RequestStatus.DRAFT).build());
    return toResponse(saved, false);
  }

  @Override
  @Transactional
  public RequestResponse createAndSubmit(String name) {
    RequestEntity saved =
        repository.save(RequestEntity.builder().name(name).status(RequestStatus.DRAFT).build());
    return submit(saved);
  }

  @Override
  @Transactional
  public RequestResponse updateDraft(Long id, String name, Long version) {
    return repository
        .findById(id)
        .map(
            existing -> {
              if (existing.getStatus() != RequestStatus.DRAFT) {
                throw ValidationErrorException.of(
                    RequestDraftErrorCode.INVALID_DRAFT_UPDATE_STATUS);
              }
              RequestEntity toSave = existing.toBuilder().name(name).version(version).build();
              return toResponse(repository.save(toSave), false);
            })
        .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
  }

  @Override
  @Transactional
  public RequestResponse submitDraft(Long id, String name, Long version) {
    RequestEntity draft =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));

    if (name != null || version != null) {
      if (name == null || version == null) {
        throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_UPDATE_STATUS);
      }
      if (draft.getStatus() != RequestStatus.DRAFT) {
        throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_UPDATE_STATUS);
      }
      draft = repository.save(draft.toBuilder().name(name).version(version).build());
    }
    return submit(draft);
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    repository.deleteById(id);
  }

  @Override
  @Transactional
  public void completeTask(Long requestId, Long taskId, TaskAction action, String comment) {
    RequestEntity request =
        repository
            .findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + requestId));

    RequestTaskEntity requestTask =
        requestTaskRepository
            .findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found with id: " + taskId));
    if (!requestTask.getRequestId().equals(requestId)) {
      throw new NotFoundException("Task " + taskId + " does not belong to request " + requestId);
    }

    Task task = taskService.createTaskQuery().taskId(requestTask.getTaskId()).singleResult();
    if (task == null) {
      throw new NotFoundException("Task not found with id: " + taskId);
    }

    if (!task.getProcessInstanceId().equals(request.getProcessInstanceId())) {
      throw new NotFoundException("Task " + taskId + " does not belong to request " + requestId);
    }

    taskService.complete(
        requestTask.getTaskId(), Map.of("action", action.name(), "comment", comment));
  }

  @Override
  public List<RequestResponse> search(RequestSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("DISTINCT r.*")
            .from("requests", "r")
            .where("upper(r.name)", Operator.LIKE, criteria.getNamePattern())
            .where("r.status", Operator.IN, criteria.getStatuses())
            .where("r.id", Operator.IN, criteria.getIds())
            .page(criteria.getPage(), criteria.getSize());
    if (criteria.getNormalizedTaskAssignees() != null) {
      builder
          .join(JoinType.INNER, "request_tasks", "rt", "rt.request_id = r.id")
          .where("upper(rt.assignee)", Operator.IN, criteria.getNormalizedTaskAssignees());
    }

    BuiltQuery query = builder.build();
    log.info(QueryLoggingHelper.format("requests.search", query, Set.of()));

    return jdbcTemplate
        .query(query.sql(), query.params(), new DataClassRowMapper<>(RequestEntity.class))
        .stream()
        .map(entity -> toResponse(entity, false))
        .toList();
  }

  private RequestResponse toResponse(RequestEntity entity, boolean includeUserTasks) {
    return RequestResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .userTasks(includeUserTasks ? findUserTasks(entity.getId()) : List.of())
        .build();
  }

  private ProcessInstance startWorkflow(Long requestId) {
    return runtimeService.startProcessInstanceByKey(REQUEST_PROCESS_KEY, requestId.toString());
  }

  private RequestResponse submit(RequestEntity draft) {
    if (draft.getStatus() != RequestStatus.DRAFT) {
      throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_SUBMIT_STATUS);
    }
    ProcessInstance processInstance = startWorkflow(draft.getId());
    RequestEntity inProgress =
        draft.toBuilder()
            .status(RequestStatus.IN_PROGRESS)
            .processInstanceId(processInstance.getProcessInstanceId())
            .build();
    return toResponse(repository.save(inProgress), false);
  }

  private List<RequestTaskResponse> findUserTasks(Long requestId) {
    return requestTaskRepository.findByRequestId(requestId).stream()
        .map(this::toTaskResponse)
        .toList();
  }

  private RequestTaskResponse toTaskResponse(RequestTaskEntity task) {
    return RequestTaskResponse.builder()
        .id(task.getId())
        .name(task.getName())
        .status(task.getStatus().name())
        .assignee(task.getAssignee())
        .build();
  }
}
