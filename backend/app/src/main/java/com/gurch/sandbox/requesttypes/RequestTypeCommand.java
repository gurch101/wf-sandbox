package com.gurch.sandbox.requesttypes;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Command payload for request type create/change operations. */
@Value
@Builder
@Schema(
    description = "Command for creating/changing request type metadata and active version mapping")
public class RequestTypeCommand {
  @Schema(description = "Stable type key", example = "loan")
  String typeKey;

  @Schema(description = "Display name", example = "Loan Request")
  String name;

  @Schema(description = "Description", example = "Loan origination request")
  String description;

  @Schema(description = "Payload handler identifier", example = "amount-positive")
  String payloadHandlerId;

  @Schema(description = "Workflow process definition key", example = "requestTypeV1Process")
  String processDefinitionKey;
}
