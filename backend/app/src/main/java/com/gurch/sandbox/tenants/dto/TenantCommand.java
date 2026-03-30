package com.gurch.sandbox.tenants.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Command payload for tenant create/update operations. */
@Value
@Builder
@Schema(description = "Command for creating or updating tenants")
public class TenantCommand {
  @Schema(description = "Unique tenant name", example = "acme")
  String name;

  @Schema(description = "Whether tenant is active", example = "true")
  Boolean active;
}
