package com.gurch.sandbox.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Detailed user DTO returned by get/create/update flows. */
@Value
@Builder
@Schema(description = "Detailed user response")
public class UserResponse {
  @Schema(description = "User identifier", example = "2")
  Integer id;

  @Schema(description = "Unique username", example = "jane.admin")
  String username;

  @Schema(description = "Unique email", example = "jane.admin@example.com")
  String email;

  @Schema(description = "Whether the user is active", example = "true")
  boolean active;

  @Schema(description = "Optional tenant identifier", example = "1")
  Integer tenantId;

  @Schema(description = "Creation timestamp")
  Instant createdAt;

  @Schema(description = "Last update timestamp")
  Instant updatedAt;

  @Schema(description = "Optimistic lock version", example = "0")
  Long version;
}
