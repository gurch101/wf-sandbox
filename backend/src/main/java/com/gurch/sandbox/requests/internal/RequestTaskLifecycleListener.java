package com.gurch.sandbox.requests.internal;

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
    requestTaskRepository.save(
        RequestTaskEntity.builder()
            .requestId(requestId)
            .processInstanceId(task.getProcessInstanceId())
            .taskId(task.getId())
            .name(task.getName())
            .status(RequestTaskStatus.ACTIVE)
            .assignee(task.getAssignee())
            .build());
  }

  private void handleComplete(DelegateTask task) {
    Optional<RequestTaskEntity> existing = requestTaskRepository.findByTaskId(task.getId());
    if (existing.isEmpty()) {
      return;
    }

    Object action = task.getVariable("action");
    Object comment = task.getVariable("comment");
    requestTaskRepository.save(
        existing.get().toBuilder()
            .status(RequestTaskStatus.COMPLETED)
            .assignee(task.getAssignee())
            .action(action == null ? null : action.toString())
            .comment(comment == null ? null : comment.toString())
            .build());
  }
}
