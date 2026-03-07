package com.gurch.sandbox.audit.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.web.CorrelationIdResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
class DefaultAuditLogService implements AuditLogApi {

  private static final int SYSTEM_USER_ID = 1;

  private final AuditLogEventRepository repository;
  private final ObjectMapper objectMapper;
  private final CurrentUserProvider currentUserProvider;
  private final CorrelationIdResolver correlationIdResolver;

  /** {@inheritDoc} */
  @Override
  public void recordCreate(
      String resourceType, Object resourceId, Object afterState, String correlationId) {
    save(resourceType, resourceId, AuditLogAction.CREATE, null, afterState, correlationId);
  }

  /** {@inheritDoc} */
  @Override
  public void recordUpdate(
      String resourceType,
      Object resourceId,
      Object beforeState,
      Object afterState,
      String correlationId) {
    JsonDiffUtils.DiffPair diffPair =
        JsonDiffUtils.diff(toJsonNode(beforeState), toJsonNode(afterState));
    if (diffPair == null) {
      return;
    }
    JsonNode updateBeforeState =
        diffPair.before() == null ? objectMapper.createObjectNode() : diffPair.before();
    JsonNode updateAfterState =
        diffPair.after() == null ? objectMapper.createObjectNode() : diffPair.after();
    save(
        resourceType,
        resourceId,
        AuditLogAction.UPDATE,
        updateBeforeState,
        updateAfterState,
        correlationId);
  }

  /** {@inheritDoc} */
  @Override
  public void recordDelete(
      String resourceType, Object resourceId, Object beforeState, String correlationId) {
    save(resourceType, resourceId, AuditLogAction.DELETE, beforeState, null, correlationId);
  }

  private void save(
      String resourceType,
      Object resourceId,
      AuditLogAction action,
      Object beforeState,
      Object afterState,
      String correlationId) {
    Integer actorUserId = currentUserProvider.currentUserId().orElse(null);
    Integer effectiveUserId = actorUserId != null ? actorUserId : SYSTEM_USER_ID;
    repository.save(
        AuditLogEventEntity.builder()
            .resourceType(resourceType)
            .resourceId(resourceId.toString())
            .action(action)
            .actorUserId(actorUserId)
            .createdBy(effectiveUserId)
            .tenantId(currentUserProvider.currentTenantId().orElse(null))
            .correlationId(correlationIdResolver.resolve(correlationId))
            .beforeState(toJsonNode(beforeState))
            .afterState(toJsonNode(afterState))
            .build());
  }

  private JsonNode toJsonNode(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof JsonNode node) {
      return node;
    }
    return objectMapper.valueToTree(value);
  }
}
