package com.gurch.sandbox.requests;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.requests.tasks.dto.RequestTaskResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/** Response DTO representing a request record. */
@Value
@Builder
@Schema(description = "Response representing a request record")
public class RequestResponse {
  @Schema(description = "Unique identifier of the request", example = "123")
  Long id;

  @Schema(description = "Request type key used for this request", example = "loan")
  String requestTypeKey;

  @Schema(description = "Resolved immutable request type version", example = "2")
  Integer requestTypeVersion;

  @Schema(description = "Current status of the request", example = "IN_PROGRESS")
  RequestStatus status;

  @Schema(description = "Payload data", implementation = Object.class)
  @JsonInclude(JsonInclude.Include.NON_NULL)
  JsonNode payload;

  @Schema(description = "Timestamp when the request was created")
  Instant createdAt;

  @Schema(description = "Timestamp when the request was last updated")
  Instant updatedAt;

  @Schema(description = "Version of the record for optimistic locking", example = "2")
  Long version;

  @Schema(description = "Workflow user tasks linked to this request")
  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  List<RequestTaskResponse> userTasks;
}
