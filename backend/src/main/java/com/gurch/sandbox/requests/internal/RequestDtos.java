package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.requests.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Value;

public interface RequestDtos {

  @Value
  @Schema(description = "Request to create a new record")
  class CreateRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Name of the request", example = "New Feature Implementation")
    String name;

    @NotNull(message = "status is required")
    @Schema(description = "Initial status of the request", example = "DRAFT")
    RequestStatus status;
  }

  @Value
  @Schema(description = "Request to update an existing record")
  class UpdateRequest {
    @NotBlank(message = "name is required")
    @Schema(description = "Updated name of the request", example = "Updated Feature Name")
    String name;

    @NotNull(message = "status is required")
    @Schema(description = "Updated status of the request", example = "IN_PROGRESS")
    RequestStatus status;

    @NotNull(message = "version is required for optimistic locking")
    @Schema(description = "Current version of the record for optimistic locking", example = "1")
    Long version;
  }

  @Value
  @Schema(description = "Response wrapper for request search results")
  class SearchResponse {
    @Schema(description = "Matching requests")
    List<com.gurch.sandbox.requests.RequestResponse> requests;
  }
}
