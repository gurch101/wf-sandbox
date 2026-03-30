package com.gurch.sandbox.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;

/** Command payload for user create/update operations. */
@Value
@Builder
@Schema(description = "Command for creating or updating users")
public class UserCommand {
  @Schema(description = "Unique username", example = "jane.admin")
  String username;

  @Schema(description = "Unique email address", example = "jane.admin@example.com")
  String email;

  @Schema(description = "Whether the user is active", example = "true")
  Boolean active;

  @Schema(description = "Optional tenant identifier", example = "1")
  Integer tenantId;
}
