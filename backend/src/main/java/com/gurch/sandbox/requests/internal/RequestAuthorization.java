package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.auth.AuthContextApi;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component("requestAuthorization")
public class RequestAuthorization {

  private static final String ADMIN_SECURITY_MANAGE = "admin.security.manage";
  private static final String REQUEST_READ = "request.read";
  private static final String REQUEST_WRITE = "request.write";
  private static final String TASK_COMPLETE = "task.complete";
  private static final String TASK_LIST = "task.list";
  private static final String TASK_CLAIM = "task.claim";
  private static final String TASK_ASSIGN = "task.assign";

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final AuthContextApi authContextRepository;

  public RequestAuthorization(
      NamedParameterJdbcTemplate jdbcTemplate, AuthContextApi authContextRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.authContextRepository = authContextRepository;
  }

  public boolean canReadRequests(Authentication authentication) {
    return hasAuthority(authentication, REQUEST_READ)
        || hasAuthority(authentication, ADMIN_SECURITY_MANAGE);
  }

  public boolean canWriteRequests(Authentication authentication) {
    return hasAuthority(authentication, REQUEST_WRITE)
        || hasAuthority(authentication, ADMIN_SECURITY_MANAGE);
  }

  public boolean canSearchRequests(Authentication authentication, RequestSearchCriteria criteria) {
    if (!canReadRequests(authentication)) {
      return false;
    }
    return criteria == null
        || !criteria.hasTaskFilters()
        || hasAuthority(authentication, TASK_LIST)
        || hasAuthority(authentication, ADMIN_SECURITY_MANAGE);
  }

  public boolean canListTasks(Authentication authentication) {
    return hasAuthority(authentication, TASK_LIST)
        || hasAuthority(authentication, ADMIN_SECURITY_MANAGE);
  }

  public boolean canClaimTask(Authentication authentication, Long taskId) {
    return canOperateTask(authentication, taskId, TASK_CLAIM);
  }

  public boolean canAssignTask(Authentication authentication, Long taskId) {
    return canOperateTask(authentication, taskId, TASK_ASSIGN);
  }

  public boolean canAccessBusinessClient(Authentication authentication, String businessClientId) {
    if (hasAuthority(authentication, ADMIN_SECURITY_MANAGE)) {
      return true;
    }
    if (businessClientId == null || businessClientId.isBlank()) {
      return false;
    }
    Optional<UUID> userId = resolveUserId(authentication);
    if (userId.isEmpty()) {
      return false;
    }
    return authContextRepository.findClientScopeIds(userId.get()).contains(businessClientId);
  }

  private boolean canOperateTask(Authentication authentication, Long taskId, String permission) {
    if (hasAuthority(authentication, ADMIN_SECURITY_MANAGE)) {
      return true;
    }
    if (!hasAuthority(authentication, permission)) {
      return false;
    }
    Optional<UUID> userId = resolveUserId(authentication);
    if (userId.isEmpty()) {
      return false;
    }
    Optional<TaskAuthorizationContext> context = findTaskAuthorizationContext(taskId);
    if (context.isEmpty()) {
      return false;
    }

    return hasWorkflowGroupMembership(userId.get(), context.get().workflowGroupCode())
        && authContextRepository
            .findClientScopeIds(userId.get())
            .contains(context.get().businessClientId());
  }

  private Optional<TaskAuthorizationContext> findTaskAuthorizationContext(Long taskId) {
    BuiltQuery query =
        SQLQueryBuilder.select("r.workflow_group_code, r.business_client_id")
            .from("request_tasks", "rt")
            .join(JoinType.INNER, "requests", "r", "r.id = rt.request_id")
            .where("rt.id", Operator.EQ, taskId)
            .build();
    return jdbcTemplate
        .query(
            query.sql(),
            query.params(),
            (rs, rowNum) ->
                new TaskAuthorizationContext(
                    rs.getString("workflow_group_code"), rs.getString("business_client_id")))
        .stream()
        .filter(
            context ->
                context.workflowGroupCode() != null
                    && !context.workflowGroupCode().isBlank()
                    && context.businessClientId() != null
                    && !context.businessClientId().isBlank())
        .findFirst();
  }

  private boolean hasWorkflowGroupMembership(UUID userId, String workflowGroupCode) {
    BuiltQuery membershipQuery =
        SQLQueryBuilder.select("uwg.user_id")
            .from("user_workflow_groups", "uwg")
            .join(JoinType.INNER, "workflow_groups", "wg", "wg.id = uwg.workflow_group_id")
            .where("uwg.user_id", Operator.EQ, userId)
            .where("wg.code", Operator.EQ, workflowGroupCode)
            .build();
    return !jdbcTemplate.queryForList(membershipQuery.sql(), membershipQuery.params()).isEmpty();
  }

  public boolean canCompleteTask(Authentication authentication, Long requestId) {
    if (hasAuthority(authentication, ADMIN_SECURITY_MANAGE)) {
      return true;
    }
    if (!hasAuthority(authentication, TASK_COMPLETE)) {
      return false;
    }

    Optional<UUID> userId = resolveUserId(authentication);
    if (userId.isEmpty()) {
      return false;
    }

    Optional<String> workflowGroupCode = findWorkflowGroupCode(requestId);
    if (workflowGroupCode.isEmpty()) {
      return false;
    }
    Optional<String> businessClientId = findRequestBusinessClientId(requestId);
    if (businessClientId.isEmpty()) {
      return false;
    }

    return hasWorkflowGroupMembership(userId.get(), workflowGroupCode.get())
        && authContextRepository.findClientScopeIds(userId.get()).contains(businessClientId.get());
  }

  private Optional<String> findWorkflowGroupCode(Long requestId) {
    BuiltQuery query =
        SQLQueryBuilder.select("r.workflow_group_code")
            .from("requests", "r")
            .where("r.id", Operator.EQ, requestId)
            .build();

    return jdbcTemplate
        .query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("workflow_group_code"))
        .stream()
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private Optional<String> findRequestBusinessClientId(Long requestId) {
    BuiltQuery query =
        SQLQueryBuilder.select("r.business_client_id")
            .from("requests", "r")
            .where("r.id", Operator.EQ, requestId)
            .build();

    return jdbcTemplate
        .query(query.sql(), query.params(), (rs, rowNum) -> rs.getString("business_client_id"))
        .stream()
        .filter(value -> value != null && !value.isBlank())
        .findFirst();
  }

  private Optional<UUID> resolveUserId(Authentication authentication) {
    if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
      String subject = jwtAuthenticationToken.getToken().getSubject();
      return parseUuid(subject);
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

  private record TaskAuthorizationContext(String workflowGroupCode, String businessClientId) {}
}
