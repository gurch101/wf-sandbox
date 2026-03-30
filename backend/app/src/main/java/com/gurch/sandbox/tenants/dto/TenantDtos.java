package com.gurch.sandbox.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** DTOs for admin tenant CRUD endpoints. */
public interface TenantDtos {

  /** Request body for creating a tenant. */
  @Value
  @Schema(description = "Create tenant request")
  class CreateTenantRequest {
    @NotBlank(message = "name is required")
    String name;

    Boolean active;
  }

  /** Request body for updating a tenant. */
  @Value
  @Schema(description = "Update tenant request")
  class UpdateTenantRequest {
    @NotBlank(message = "name is required")
    String name;

    Boolean active;

    @NotNull(message = "version is required")
    Long version;
  }
}
