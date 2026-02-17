package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

/** DTOs used by the request HTTP API. */
public interface RequestDtos {

  /** Request payload for creating a draft request. */
  @Value
  @Schema(description = "Request to create a new draft")
  class CreateDraftRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Name of the request", example = "New Feature Implementation")
    String name;
  }

  /** Request payload for creating and submitting a new request. */
  @Value
  @Schema(description = "Request to create and submit a request")
  class SubmitRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Name of the request", example = "Submit New Request")
    String name;
  }

  /** Request payload for updating a draft request. */
  @Value
  @Schema(description = "Request to update an existing draft")
  class UpdateDraftRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Updated name of the draft request", example = "Updated Feature Name")
    String name;

    @NotNull(message = "version is required for optimistic locking")
    @Schema(description = "Current version of the record for optimistic locking", example = "1")
    Long version;
  }

  /** Request payload for completing a user task. */
  @Value
  @Schema(description = "Request to complete a workflow user task")
  class CompleteTaskRequest {
    @NotNull(message = "action is required")
    @Schema(description = "Action performed for this task", example = "APPROVED")
    TaskAction action;

    @NotBlank(message = "comment is required")
    @Schema(description = "Comment captured with the task completion", example = "Looks good")
    String comment;
  }

  /** Response wrapper returned by request search endpoint. */
  @Value
  @Schema(description = "Response wrapper for request search results")
  class SearchResponse {
    @Schema(description = "Matching requests")
    List<com.gurch.sandbox.requests.RequestResponse> requests;
  }
}
