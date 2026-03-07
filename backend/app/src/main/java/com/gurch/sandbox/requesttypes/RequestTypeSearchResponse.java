package com.gurch.sandbox.requesttypes;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Summary response for request type search. */
@Value
@Builder
@Schema(description = "Request type summary with current active version mapping")
public class RequestTypeSearchResponse {
  @Schema(description = "Stable type key", example = "loan")
  String typeKey;

  @Schema(description = "Display name", example = "Loan Request")
  String name;

  @Schema(description = "Description", example = "Loan origination request")
  String description;

  @Schema(description = "Whether the type is active", example = "true")
  boolean active;

  @Schema(description = "Current active immutable version", example = "2")
  Integer activeVersion;

  @Schema(description = "Payload handler id for active version", example = "amount-positive")
  String payloadHandlerId;

  @Schema(
      description = "Process definition key for active version",
      example = "requestTypeV2Process")
  String processDefinitionKey;
}
