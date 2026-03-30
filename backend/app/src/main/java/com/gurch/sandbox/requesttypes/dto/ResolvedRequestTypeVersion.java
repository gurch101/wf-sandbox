package com.gurch.sandbox.requesttypes.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** DTO describing a resolved request type version mapping. */
@Value
@Builder
@Schema(description = "Resolved latest active request type version")
public class ResolvedRequestTypeVersion {
  @Schema(description = "Request type key", example = "loan")
  String typeKey;

  @Schema(description = "Resolved immutable version", example = "2")
  Integer version;

  @Schema(description = "Workflow process definition key", example = "requestTypeV2Process")
  String processDefinitionKey;
}
