package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.auth.AuthContextApi;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.QueryLoggingHelper;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.CreateRequestCommand;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestDraftErrorCode;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestSearchResponse;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.RequestTaskResponse;
import com.gurch.sandbox.requests.RequestTaskSearchCriteria;
import com.gurch.sandbox.requests.TaskAction;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.finos.fluxnova.bpm.engine.RuntimeService;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.runtime.ProcessInstance;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class DefaultRequestService implements RequestApi {

  private static final String ADMIN_SECURITY_MANAGE = "admin.security.manage";

  private final RequestRepository repository;
  private final RequestTaskRepository requestTaskRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final AuthContextApi authContextRepository;
  private final RuntimeService runtimeService;
  private final TaskService taskService;
  private final RequestTypeApi requestTypeApi;
  private final RequestPayloadHandlerRegistry payloadHandlerRegistry;

  @Override
  public Optional<RequestResponse> findById(Long id) {
    return repository.findById(id).map(entity -> toResponse(entity, true, true));
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
                .payloadJson(command.getPayload())
                .status(RequestStatus.DRAFT)
                .build());
    return saved.getId();
  }

  @Override
  @Transactional
  public Long updateDraft(Long id, JsonNode payload, Long version) {
    RequestEntity draft =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
    if (draft.getStatus() != RequestStatus.DRAFT) {
      throw ValidationErrorException.of(RequestDraftErrorCode.INVALID_DRAFT_UPDATE_STATUS);
    }

    RequestEntity updated =
        repository.save(draft.toBuilder().payloadJson(payload).version(version).build());

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
    return submitPersistedRequest(draft, resolved, draft.getPayloadJson());
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
                .payloadJson(command.getPayload())
                .status(RequestStatus.DRAFT)
                .build());

    return submitPersistedRequest(draft, resolved, command.getPayload());
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

    Map<String, Object> variables = new HashMap<>();
    variables.put("action", action.name());
    if (comment != null && !comment.isBlank()) {
      taskService.createComment(requestTask.getTaskId(), request.getProcessInstanceId(), comment);
    }
    taskService.complete(requestTask.getTaskId(), variables);
  }

  @Override
  public List<RequestSearchResponse> search(RequestSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select(
                "DISTINCT r.id, r.request_type_key AS requestTypeKey, "
                    + "r.request_type_version AS requestTypeVersion, "
                    + "r.status, r.created_at AS createdAt, r.updated_at AS updatedAt, r.version")
            .from("requests", "r")
            .where("r.status", Operator.IN, criteria.getStatuses())
            .where("r.id", Operator.IN, criteria.getIds())
            .where("r.request_type_key", Operator.IN, criteria.getNormalizedRequestTypeKeys())
            .page(criteria.getPage(), criteria.getSize());
    if (criteria.getNormalizedTaskAssignees() != null) {
      builder
          .join(JoinType.INNER, "request_tasks", "rt", "rt.request_id = r.id")
          .where("upper(rt.assignee)", Operator.IN, criteria.getNormalizedTaskAssignees());
    }
    if (!applyClientScopeFilter(builder)) {
      return List.of();
    }

    BuiltQuery query = builder.build();
    log.info(QueryLoggingHelper.format("requests.search", query, Set.of()));

    return jdbcTemplate.query(
        query.sql(), query.params(), new DataClassRowMapper<>(RequestSearchResponse.class));
  }

  @Override
  public List<RequestTaskResponse> listTasks(RequestTaskSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("rt.*")
            .from("request_tasks", "rt")
            .join(JoinType.INNER, "requests", "r", "r.id = rt.request_id")
            .where("rt.request_id", Operator.EQ, criteria.getRequestId())
            .where("upper(rt.assignee)", Operator.EQ, criteria.getNormalizedAssignee())
            .where("rt.status", Operator.EQ, RequestTaskStatus.ACTIVE)
            .orderBy("+rt.id")
            .page(criteria.getPage(), criteria.getSize());

    if (!applyClientScopeFilter(builder)) {
      return List.of();
    }

    BuiltQuery query = builder.build();
    log.info(QueryLoggingHelper.format("tasks.search", query, Set.of()));
    return jdbcTemplate
        .query(query.sql(), query.params(), new DataClassRowMapper<>(RequestTaskEntity.class))
        .stream()
        .map(this::toTaskResponse)
        .toList();
  }

  @Override
  @Transactional
  public void claimTask(Long taskId, String assignee) {
    assignTask(taskId, assignee);
  }

  @Override
  @Transactional
  public void assignTask(Long taskId, String assignee) {
    RequestTaskEntity requestTask =
        requestTaskRepository
            .findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found with id: " + taskId));

    Task task = taskService.createTaskQuery().taskId(requestTask.getTaskId()).singleResult();
    if (task == null) {
      throw new NotFoundException("Task not found with id: " + taskId);
    }

    taskService.setAssignee(requestTask.getTaskId(), assignee);
    requestTaskRepository.save(requestTask.toBuilder().assignee(assignee).build());
  }

  private RequestResponse toResponse(
      RequestEntity entity, boolean includeUserTasks, boolean includePayload) {
    return RequestResponse.builder()
        .id(entity.getId())
        .requestTypeKey(entity.getRequestTypeKey())
        .requestTypeVersion(entity.getRequestTypeVersion())
        .status(entity.getStatus())
        .payload(includePayload ? entity.getPayloadJson() : null)
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .businessClientId(entity.getBusinessClientId())
        .userTasks(includeUserTasks ? findUserTasks(entity.getId()) : List.of())
        .build();
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

  private RequestResponse submitPersistedRequest(
      RequestEntity baseEntity, ResolvedRequestTypeVersion resolved, JsonNode payload) {
    payloadHandlerRegistry.validate(resolved.getPayloadHandlerId(), payload);

    RequestEntity submitted =
        repository.save(
            baseEntity.toBuilder()
                .requestTypeKey(resolved.getTypeKey())
                .requestTypeVersion(resolved.getVersion())
                .payloadJson(payload)
                .status(RequestStatus.SUBMITTED)
                .build());

    ProcessInstance processInstance =
        runtimeService.startProcessInstanceByKey(
            resolved.getProcessDefinitionKey(), submitted.getId().toString());

    RequestEntity inProgress =
        repository.save(
            submitted.toBuilder()
                .status(RequestStatus.IN_PROGRESS)
                .processInstanceId(processInstance.getProcessInstanceId())
                .build());

    return toResponse(inProgress, false, false);
  }

  private boolean applyClientScopeFilter(SQLQueryBuilder builder) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || hasAuthority(authentication, ADMIN_SECURITY_MANAGE)) {
      return true;
    }
    Optional<UUID> userId = resolveUserId(authentication);
    if (userId.isEmpty()) {
      return false;
    }
    List<String> scopes = authContextRepository.findClientScopeIds(userId.get());
    if (scopes.isEmpty()) {
      return false;
    }
    builder.where("r.business_client_id", Operator.IN, scopes);
    return true;
  }

  private Optional<UUID> resolveUserId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      return parseUuid(jwtAuthenticationToken.getToken().getSubject());
    }
    return parseUuid(authentication.getName());
  }

  private Optional<UUID> parseUuid(String value) {
    try {
      return Optional.of(UUID.fromString(value));
    } catch (Exception ignored) {
      return Optional.empty();
    }
  }

  private boolean hasAuthority(Authentication authentication, String authority) {
    for (GrantedAuthority grantedAuthority : authentication.getAuthorities()) {
      if (authority.equals(grantedAuthority.getAuthority())) {
        return true;
      }
    }
    return false;
  }
}
