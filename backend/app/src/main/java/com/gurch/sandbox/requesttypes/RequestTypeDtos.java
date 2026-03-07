package com.gurch.sandbox.requesttypes;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Value;

/** DTOs for internal request type management endpoints. */
public interface RequestTypeDtos {

  /** Request body for creating a new type and its initial version. */
  @Value
  @Schema(description = "Create request type with initial active version")
  class CreateTypeRequest {
    @NotBlank(message = "typeKey is required")
    String typeKey;

    @NotBlank(message = "name is required")
    String name;

    String description;

    @NotBlank(message = "payloadHandlerId is required")
    String payloadHandlerId;

    @NotBlank(message = "processDefinitionKey is required")
    String processDefinitionKey;
  }

  /** Request body for appending a new active version to an existing type. */
  @Value
  @Schema(description = "Change request type by appending new active version")
  class ChangeTypeRequest {
    @NotBlank(message = "name is required")
    String name;

    String description;

    @NotBlank(message = "payloadHandlerId is required")
    String payloadHandlerId;

    @NotBlank(message = "processDefinitionKey is required")
    String processDefinitionKey;
  }
}
