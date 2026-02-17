package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Data transfer object representing a request record in API responses. */
@Value
@Builder
@Schema(description = "Response representing a request record")
public class RequestResponse {
  @Schema(description = "Unique identifier of the request", example = "123")
  Long id;

  @Schema(description = "Name of the request", example = "Sample Request")
  String name;

  @Schema(description = "Current status of the request", example = "COMPLETED")
  RequestStatus status;

  @Schema(description = "Timestamp when the request was created")
  Instant createdAt;

  @Schema(description = "Timestamp when the request was last updated")
  Instant updatedAt;

  @Schema(description = "Version of the record for optimistic locking", example = "2")
  Long version;
}
