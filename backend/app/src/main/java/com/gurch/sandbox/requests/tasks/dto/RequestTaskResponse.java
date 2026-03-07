package com.gurch.sandbox.requests.tasks.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** User task details associated with a request workflow instance. */
@Value
@Builder
@Schema(description = "User task details for the request workflow")
public class RequestTaskResponse {

  @Schema(description = "Request task identifier", example = "123")
  Long id;

  @Schema(description = "Task display name", example = "Approve Request")
  String name;

  @Schema(description = "Task status", example = "ACTIVE")
  String status;

  @Schema(description = "Task assignee", example = "demo")
  String assignee;
}
