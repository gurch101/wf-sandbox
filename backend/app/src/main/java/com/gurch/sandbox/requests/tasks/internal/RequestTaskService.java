package com.gurch.sandbox.requests.tasks.internal;

import com.gurch.sandbox.requests.tasks.RequestTaskApi;
import com.gurch.sandbox.requests.tasks.dto.RequestTaskResponse;
import com.gurch.sandbox.requests.tasks.dto.TaskAction;
import com.gurch.sandbox.web.NotFoundException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.finos.fluxnova.bpm.engine.TaskService;
import org.finos.fluxnova.bpm.engine.task.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RequestTaskService implements RequestTaskApi {

  private final RequestTaskRepository repository;
  private final TaskService taskService;

  @Override
  public List<RequestTaskResponse> findByRequestId(Long requestId) {
    return repository.findByRequestId(requestId).stream().map(this::toTaskResponse).toList();
  }

  @Override
  @Transactional
  public void completeTask(Long requestId, Long taskId, TaskAction action, String comment) {
    RequestTaskEntity requestTask =
        repository
            .findById(taskId)
            .orElseThrow(() -> new NotFoundException("Task not found with id: " + taskId));
    if (!requestTask.getRequestId().equals(requestId)) {
      throw new NotFoundException("Task " + taskId + " does not belong to request " + requestId);
    }

    Task task = taskService.createTaskQuery().taskId(requestTask.getTaskId()).singleResult();
    if (task == null) {
      throw new NotFoundException("Task not found with id: " + taskId);
    }
    // TODO: Replace requestTask.processInstanceId lookup with a public request lookup API and
    // remove denormalized processInstanceId from request_tasks.
    if (!task.getProcessInstanceId().equals(requestTask.getProcessInstanceId())) {
      throw new NotFoundException("Task " + taskId + " does not belong to request " + requestId);
    }

    if (comment != null && !comment.isBlank()) {
      taskService.createComment(
          requestTask.getTaskId(), requestTask.getProcessInstanceId(), comment);
    }
    taskService.complete(requestTask.getTaskId(), Map.of("action", action.name()));
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
