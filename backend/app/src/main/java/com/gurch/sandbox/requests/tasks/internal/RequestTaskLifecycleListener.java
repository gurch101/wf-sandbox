package com.gurch.sandbox.requests.tasks.internal;

import com.gurch.sandbox.requests.activity.RequestActivityApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.delegate.DelegateTask;
import org.finos.fluxnova.bpm.engine.delegate.TaskListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("requestTaskLifecycleListener")
@RequiredArgsConstructor
public class RequestTaskLifecycleListener implements TaskListener {

  private static final String EVENT_CREATE = "create";
  private static final String EVENT_COMPLETE = "complete";

  private final RequestTaskRepository requestTaskRepository;
  private final RequestActivityApi requestActivityApi;

  @Override
  @Transactional
  public void notify(DelegateTask delegateTask) {
    String event = delegateTask.getEventName();
    if (EVENT_CREATE.equals(event)) {
      handleCreate(delegateTask);
      return;
    }
    if (EVENT_COMPLETE.equals(event)) {
      handleComplete(delegateTask);
    }
  }

  private void handleCreate(DelegateTask task) {
    if (requestTaskRepository.findByTaskId(task.getId()).isPresent()) {
      return;
    }

    Long requestId = Long.valueOf(task.getExecution().getProcessBusinessKey());
    RequestTaskEntity created =
        requestTaskRepository.save(
            RequestTaskEntity.builder()
                .requestId(requestId)
                .processInstanceId(task.getProcessInstanceId())
                .taskId(task.getId())
                .name(task.getName())
                .status(RequestTaskStatus.ACTIVE)
                .assignee(task.getAssignee())
                .build());
    if (created.getAssignee() != null) {
      requestActivityApi.recordTaskAssigned(
          requestId,
          created.getId(),
          created.getTaskId(),
          created.getName(),
          created.getAssignee(),
          task.getProcessInstanceId());
    }
  }

  private void handleComplete(DelegateTask task) {
    Optional<RequestTaskEntity> existing = requestTaskRepository.findByTaskId(task.getId());
    if (existing.isEmpty()) {
      return;
    }

    Object action = task.getVariable("action");
    RequestTaskEntity completed =
        requestTaskRepository.save(
            existing.get().toBuilder()
                .status(RequestTaskStatus.COMPLETED)
                .assignee(task.getAssignee())
                .action(action == null ? null : action.toString())
                .build());
    requestActivityApi.recordTaskCompleted(
        completed.getRequestId(),
        completed.getId(),
        completed.getTaskId(),
        completed.getName(),
        completed.getAssignee(),
        completed.getAction(),
        task.getProcessInstanceId());
  }
}
