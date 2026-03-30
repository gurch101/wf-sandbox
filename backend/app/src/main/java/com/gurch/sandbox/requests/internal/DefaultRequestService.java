package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestDraftErrorCode;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.activity.RequestActivityApi;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivitySearchCriteria;
import com.gurch.sandbox.requests.dto.CreateRequestCommand;
import com.gurch.sandbox.requests.dto.RequestResponse;
import com.gurch.sandbox.requests.dto.RequestSearchCriteria;
import com.gurch.sandbox.requests.dto.RequestSearchResponse;
import com.gurch.sandbox.requests.internal.models.RequestEntity;
import com.gurch.sandbox.requests.tasks.RequestTaskApi;
import com.gurch.sandbox.requests.tasks.dto.RequestTaskResponse;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.dto.ResolvedRequestTypeVersion;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.web.CorrelationIdResolver;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultRequestService implements RequestApi {

  private static final String REQUESTS_RESOURCE_TYPE = "requests";

  private final RequestRepository repository;
  private final RequestTaskApi requestTaskApi;
  private final RuntimeService runtimeService;
  private final RequestTypeApi requestTypeApi;
  private final SearchExecutor searchExecutor;
  private final AuditLogApi auditLogApi;
  private final RequestActivityApi requestActivityApi;
  private final CorrelationIdResolver correlationIdResolver;

  @Override
  public Optional<RequestResponse> findById(Long id) {
    return repository.findById(id).map(entity -> toResponse(entity, true));
  }

  @Override
  @Transactional
  public Long createDraft(CreateRequestCommand command) {
    ResolvedRequestTypeVersion resolved =
        requestTypeApi.resolveLatestActive(command.getRequestTypeKey());

    RequestEntity saved =
        repository.save(
            RequestEntity.builder()
                .requestTypeKey(command.getRequestTypeKey())
                .requestTypeVersion(resolved.getVersion())
                .status(RequestStatus.DRAFT)
                .build());
    String correlationId = correlationIdResolver.resolve(null);
    auditLogApi.recordCreate(REQUESTS_RESOURCE_TYPE, saved.getId(), saved, correlationId);
    requestActivityApi.recordStatusChanged(
        saved.getId(), null, saved.getStatus(), null, saved.getProcessInstanceId(), correlationId);
    return saved.getId();
  }

  @Override
  @Transactional
  public Long updateDraft(Long id, Long version) {
    RequestEntity draft =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
    if (draft.getStatus() != RequestStatus.DRAFT) {
      throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_UPDATE_STATUS);
    }

    RequestEntity beforeState = draft;
    RequestEntity updated = repository.save(draft.toBuilder().version(version).build());
    auditLogApi.recordUpdate(
        REQUESTS_RESOURCE_TYPE,
        updated.getId(),
        beforeState,
        updated,
        correlationIdResolver.resolve(null));

    return updated.getId();
  }

  @Override
  @Transactional
  public RequestResponse submitDraft(Long id) {
    RequestEntity draft =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
    if (draft.getStatus() != RequestStatus.DRAFT) {
      throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_SUBMIT_STATUS);
    }

    ResolvedRequestTypeVersion resolved =
        draft.getRequestTypeVersion() == null
            ? requestTypeApi.resolveLatestActive(draft.getRequestTypeKey())
            : requestTypeApi.resolveVersion(
                draft.getRequestTypeKey(), draft.getRequestTypeVersion());
    return submitPersistedRequest(draft, resolved);
  }

  @Override
  @Transactional
  public RequestResponse createAndSubmit(CreateRequestCommand command) {
    ResolvedRequestTypeVersion resolved =
        requestTypeApi.resolveLatestActive(command.getRequestTypeKey());

    RequestEntity draft =
        repository.save(
            RequestEntity.builder()
                .requestTypeKey(command.getRequestTypeKey())
                .requestTypeVersion(resolved.getVersion())
                .status(RequestStatus.DRAFT)
                .build());
    String correlationId = correlationIdResolver.resolve(null);
    auditLogApi.recordCreate(REQUESTS_RESOURCE_TYPE, draft.getId(), draft, correlationId);
    requestActivityApi.recordStatusChanged(
        draft.getId(), null, draft.getStatus(), null, draft.getProcessInstanceId(), correlationId);

    return submitPersistedRequest(draft, resolved);
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    RequestEntity existing =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
    repository.delete(existing);
    auditLogApi.recordDelete(
        REQUESTS_RESOURCE_TYPE, id, existing, correlationIdResolver.resolve(null));
  }

  @Override
  @Transactional
  public void completeTask(Long requestId, Long taskId, TaskAction action, String comment) {
    requestTaskApi.completeTask(requestId, taskId, action, comment);
  }

  @Override
  public PagedResponse<RequestActivityEventResponse> searchActivity(
      Long id, RequestActivitySearchCriteria criteria) {
    return requestActivityApi.search(id, criteria);
  }

  @Override
  public PagedResponse<RequestSearchResponse> search(RequestSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.newBuilder()
            .select(
                "DISTINCT r.id, r.request_type_key AS requestTypeKey, "
                    + "r.request_type_version AS requestTypeVersion, "
                    + "r.status, r.created_at AS createdAt, r.updated_at AS updatedAt, r.version")
            .from("requests", "r")
            .where("r.status", Operator.IN, criteria.getStatuses())
            .where("r.id", Operator.IN, criteria.getIds())
            .where("r.request_type_key", Operator.IN, criteria.getNormalizedRequestTypeKeys());

    if (criteria.getNormalizedTaskAssignees() != null) {
      builder
          .join(JoinType.INNER, "request_tasks", "rt", "rt.request_id = r.id")
          .where("upper(rt.assignee)", Operator.IN, criteria.getNormalizedTaskAssignees());
    }

    return searchExecutor.execute(builder, criteria, RequestSearchResponse.class);
  }

  private RequestResponse toResponse(RequestEntity entity, boolean includeUserTasks) {
    return RequestResponse.builder()
        .id(entity.getId())
        .requestTypeKey(entity.getRequestTypeKey())
        .requestTypeVersion(entity.getRequestTypeVersion())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .userTasks(includeUserTasks ? findUserTasks(entity.getId()) : List.of())
        .build();
  }

  private List<RequestTaskResponse> findUserTasks(Long requestId) {
    return requestTaskApi.findByRequestId(requestId);
  }

  private RequestResponse submitPersistedRequest(
      RequestEntity baseEntity, ResolvedRequestTypeVersion resolved) {
    String correlationId = correlationIdResolver.resolve(null);

    RequestEntity beforeSubmittedState = baseEntity;
    RequestEntity submitted =
        repository.save(
            baseEntity.toBuilder()
                .requestTypeKey(resolved.getTypeKey())
                .requestTypeVersion(resolved.getVersion())
                .status(RequestStatus.SUBMITTED)
                .build());
    auditLogApi.recordUpdate(
        REQUESTS_RESOURCE_TYPE, submitted.getId(), beforeSubmittedState, submitted, correlationId);
    requestActivityApi.recordStatusChanged(
        submitted.getId(),
        baseEntity.getStatus(),
        submitted.getStatus(),
        null,
        submitted.getProcessInstanceId(),
        correlationId);

    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(
            resolved.getProcessDefinitionKey(), submitted.getId().toString());

    RequestEntity beforeInProgressState = submitted;
    RequestEntity inProgress =
        repository.save(
            submitted.toBuilder()
                .status(RequestStatus.IN_PROGRESS)
                .processInstanceId(processInstance.getProcessInstanceId())
                .build());
    String inProgressCorrelation =
        correlationId != null ? correlationId : processInstance.getProcessInstanceId();
    auditLogApi.recordUpdate(
        REQUESTS_RESOURCE_TYPE,
        inProgress.getId(),
        beforeInProgressState,
        inProgress,
        inProgressCorrelation);
    requestActivityApi.recordStatusChanged(
        inProgress.getId(),
        submitted.getStatus(),
        inProgress.getStatus(),
        null,
        inProgress.getProcessInstanceId(),
        inProgressCorrelation);

    return toResponse(inProgress, false);
  }
}
