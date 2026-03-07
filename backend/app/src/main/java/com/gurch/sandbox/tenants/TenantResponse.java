package com.gurch.sandbox.tenants;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Detailed tenant DTO returned by get/create/update flows. */
@Value
@Builder
@Schema(description = "Detailed tenant response")
public class TenantResponse {
  @Schema(description = "Tenant identifier", example = "1")
  Integer id;

  @Schema(description = "Unique tenant name", example = "acme")
  String name;

  @Schema(description = "Whether tenant is active", example = "true")
  boolean active;

  @Schema(description = "Creation timestamp")
  Instant createdAt;

  @Schema(description = "Last update timestamp")
  Instant updatedAt;

  @Schema(description = "Optimistic lock version", example = "0")
  Long version;
}
