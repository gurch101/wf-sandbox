package com.gurch.sandbox.requests;

import com.gurch.sandbox.idempotency.NotIdempotent;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

  private final RequestApi requestApi;

  public TaskController(RequestApi requestApi) {
    this.requestApi = requestApi;
  }

  @GetMapping
  @NotIdempotent
  @PreAuthorize("@requestAuthorization.canListTasks(authentication)")
  public List<RequestTaskResponse> list(RequestTaskSearchCriteria criteria) {
    return requestApi.listTasks(criteria);
  }

  @PutMapping("/{taskId}/claim")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@requestAuthorization.canClaimTask(authentication, #taskId)")
  public void claim(@PathVariable Long taskId, Authentication authentication) {
    requestApi.claimTask(taskId, authentication.getName());
  }

  @PutMapping("/{taskId}/assign")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@requestAuthorization.canAssignTask(authentication, #taskId)")
  public void assign(
      @PathVariable Long taskId, @Valid @RequestBody TaskDtos.AssignTaskRequest request) {
    requestApi.assignTask(taskId, request.getAssignee());
  }
}
