package com.gurch.sandbox.requests;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** Request module DTOs used by controller endpoints. */
public interface RequestDtos {

  /** Request payload for creating and submitting a request. */
  @Value
  @Schema(description = "Request payload for creating and submitting a new request")
  class CreateRequest {
    @NotBlank(message = "requestTypeKey is required")
    @Schema(description = "Request type key", example = "loan")
    String requestTypeKey;

    @NotNull(message = "payload is required")
    @Schema(description = "Request payload", implementation = Object.class)
    JsonNode payload;
  }

  /** Request payload for updating an existing draft request. */
  @Value
  @Schema(description = "Request payload for updating an existing draft request")
  class UpdateDraftRequest {
    @NotNull(message = "payload is required")
    @Schema(description = "Request payload", implementation = Object.class)
    JsonNode payload;

    @NotNull(message = "version is required for optimistic locking")
    @Schema(description = "Current version of the record for optimistic locking", example = "1")
    Long version;
  }

  /** Request payload for completing a workflow user task. */
  @Value
  @Schema(description = "Request payload for completing a workflow user task")
  class CompleteTaskRequest {
    @NotNull(message = "action is required")
    @Schema(description = "Task action")
    TaskAction action;

    @Schema(description = "Task completion comment")
    String comment;
  }
}
