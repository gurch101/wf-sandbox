package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;

/** DTOs for task-related endpoints. */
public interface TaskDtos {

  /** Request payload for assigning a task to a user. */
  @Value
  @Schema(description = "Request payload for assigning a task")
  class AssignTaskRequest {
    @NotBlank(message = "assignee is required")
    @Schema(description = "Assignee identifier", example = "demo")
    String assignee;
  }
}
