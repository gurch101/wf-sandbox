package com.gurch.sandbox.requests.activity.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.activity.RequestActivityApi;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventType;
import com.gurch.sandbox.requests.activity.dto.RequestActivityPayload;
import com.gurch.sandbox.requests.activity.dto.RequestActivitySearchCriteria;
import com.gurch.sandbox.requests.activity.dto.RequestStatusChangedActivityPayload;
import com.gurch.sandbox.requests.activity.dto.RequestTaskAssignedActivityPayload;
import com.gurch.sandbox.requests.activity.dto.RequestTaskCompletedActivityPayload;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.web.CorrelationIdResolver;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DefaultRequestActivityService implements RequestActivityApi {

  private static final int SYSTEM_USER_ID = 1;

  private final SearchExecutor searchExecutor;
  private final RequestActivityEventRepository repository;
  private final ObjectMapper objectMapper;
  private final AuditorAware<Integer> auditorAware;
  private final CorrelationIdResolver correlationIdResolver;

  @Override
  public PagedResponse<RequestActivityEventResponse> search(
      Long requestId, RequestActivitySearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select(
                "rae.id, rae.request_id AS requestId, rae.event_type AS eventType, "
                    + "rae.actor_user_id AS actorUserId, rae.correlation_id AS correlationId, "
                    + "rae.payload::text AS payloadText, rae.created_at AS createdAt")
            .from("request_activity_events", "rae")
            .where("rae.request_id", Operator.EQ, requestId)
            .where("rae.event_type", Operator.IN, criteria.getEventTypes())
            .where("rae.created_at", Operator.GTE, criteria.getCreatedAtFrom())
            .where("rae.created_at", Operator.LTE, criteria.getCreatedAtTo())
            .orderBy("-rae.created_at")
            .orderBy("-rae.id");
    PagedResponse<RequestActivityRow> rows =
        searchExecutor.execute(builder, criteria, RequestActivityRow.class);
    return new PagedResponse<>(
        rows.items().stream().map(this::toActivityResponse).toList(),
        rows.totalElements(),
        rows.page(),
        rows.size());
  }

  @Override
  public void recordStatusChanged(
      Long requestId,
      RequestStatus previousStatus,
      RequestStatus currentStatus,
      Long taskId,
      String processInstanceId,
      String correlationId) {
    repository.save(
        RequestActivityEventEntity.builder()
            .requestId(requestId)
            .eventType(RequestActivityEventType.STATUS_CHANGED)
            .actorUserId(auditorAware.getCurrentAuditor().orElse(null))
            .createdBy(resolveCreatedBy())
            .correlationId(correlationIdResolver.resolve(correlationId))
            .payload(
                objectMapper.valueToTree(
                    new RequestStatusChangedActivityPayload(
                        previousStatus == null ? null : previousStatus.name(),
                        currentStatus == null ? null : currentStatus.name(),
                        taskId,
                        processInstanceId)))
            .build());
  }

  @Override
  public void recordTaskAssigned(
      Long requestId,
      Long taskId,
      String taskEngineId,
      String taskName,
      String assignee,
      String correlationId) {
    repository.save(
        RequestActivityEventEntity.builder()
            .requestId(requestId)
            .eventType(RequestActivityEventType.TASK_ASSIGNED)
            .actorUserId(auditorAware.getCurrentAuditor().orElse(null))
            .createdBy(resolveCreatedBy())
            .correlationId(correlationIdResolver.resolve(correlationId))
            .payload(
                objectMapper.valueToTree(
                    new RequestTaskAssignedActivityPayload(
                        taskId, taskEngineId, taskName, assignee)))
            .build());
  }

  @Override
  public void recordTaskCompleted(
      Long requestId,
      Long taskId,
      String taskEngineId,
      String taskName,
      String assignee,
      String action,
      String correlationId) {
    repository.save(
        RequestActivityEventEntity.builder()
            .requestId(requestId)
            .eventType(RequestActivityEventType.TASK_COMPLETED)
            .actorUserId(auditorAware.getCurrentAuditor().orElse(null))
            .createdBy(resolveCreatedBy())
            .correlationId(correlationIdResolver.resolve(correlationId))
            .payload(
                objectMapper.valueToTree(
                    new RequestTaskCompletedActivityPayload(
                        taskId, taskEngineId, taskName, assignee, action)))
            .build());
  }

  private Integer resolveCreatedBy() {
    return auditorAware.getCurrentAuditor().orElse(SYSTEM_USER_ID);
  }

  private RequestActivityPayload toActivityPayload(
      RequestActivityEventType eventType, String payloadText) throws Exception {
    return switch (eventType) {
      case STATUS_CHANGED ->
          objectMapper.readValue(payloadText, RequestStatusChangedActivityPayload.class);
      case TASK_ASSIGNED ->
          objectMapper.readValue(payloadText, RequestTaskAssignedActivityPayload.class);
      case TASK_COMPLETED ->
          objectMapper.readValue(payloadText, RequestTaskCompletedActivityPayload.class);
    };
  }

  private RequestActivityEventResponse toActivityResponse(RequestActivityRow row) {
    try {
      RequestActivityEventType eventType = RequestActivityEventType.valueOf(row.eventType());
      return new RequestActivityEventResponse(
          row.id(),
          row.requestId(),
          eventType,
          row.actorUserId(),
          row.correlationId(),
          toActivityPayload(eventType, row.payloadText()),
          row.createdAt());
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to map request activity payload", ex);
    }
  }

  private record RequestActivityRow(
      Long id,
      Long requestId,
      String eventType,
      Integer actorUserId,
      String correlationId,
      String payloadText,
      Instant createdAt) {}
}
