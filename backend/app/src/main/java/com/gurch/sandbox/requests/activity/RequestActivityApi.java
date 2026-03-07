package com.gurch.sandbox.requests.activity;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivitySearchCriteria;

/** API for recording and querying request activity events. */
public interface RequestActivityApi {

  /** Returns paged activity events for a request. */
  PagedResponse<RequestActivityEventResponse> search(
      Long requestId, RequestActivitySearchCriteria criteria);

  /** Records a request status transition event. */
  default void recordStatusChanged(
      Long requestId, RequestStatus previousStatus, RequestStatus currentStatus, Long taskId) {
    recordStatusChanged(requestId, previousStatus, currentStatus, taskId, null, null);
  }

  /** Records a request status transition event. */
  default void recordStatusChanged(
      Long requestId,
      RequestStatus previousStatus,
      RequestStatus currentStatus,
      Long taskId,
      String processInstanceId) {
    recordStatusChanged(requestId, previousStatus, currentStatus, taskId, processInstanceId, null);
  }

  /** Records a request status transition event. */
  void recordStatusChanged(
      Long requestId,
      RequestStatus previousStatus,
      RequestStatus currentStatus,
      Long taskId,
      String processInstanceId,
      String correlationId);

  /** Records a task-assignment event. */
  default void recordTaskAssigned(
      Long requestId, Long taskId, String taskEngineId, String taskName, String assignee) {
    recordTaskAssigned(requestId, taskId, taskEngineId, taskName, assignee, null);
  }

  /** Records a task-assignment event. */
  void recordTaskAssigned(
      Long requestId,
      Long taskId,
      String taskEngineId,
      String taskName,
      String assignee,
      String correlationId);

  /** Records a task-completion event. */
  default void recordTaskCompleted(
      Long requestId,
      Long taskId,
      String taskEngineId,
      String taskName,
      String assignee,
      String action) {
    recordTaskCompleted(requestId, taskId, taskEngineId, taskName, assignee, action, null);
  }

  /** Records a task-completion event. */
  void recordTaskCompleted(
      Long requestId,
      Long taskId,
      String taskEngineId,
      String taskName,
      String assignee,
      String action,
      String correlationId);
}
