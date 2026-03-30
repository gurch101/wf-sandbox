package com.gurch.sandbox.workflows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Summary response for deployed workflow process definitions. */
@Value
@Builder
@Schema(description = "Deployed workflow process definition")
public class WorkflowDefinitionResponse {
  @Schema(description = "Process definition id")
  String id;

  @Schema(description = "Process definition key", example = "requestTypeV1Process")
  String key;

  @Schema(description = "Process definition name")
  String name;

  @Schema(description = "Version")
  Integer version;
}
