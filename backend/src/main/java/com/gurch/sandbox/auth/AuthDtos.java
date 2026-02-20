package com.gurch.sandbox.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Value;

/** DTOs for authenticated principal context endpoints. */
public interface AuthDtos {

  /** Response payload describing the authenticated principal. */
  @Value
  @Schema(description = "Current authenticated principal context")
  class MeResponse {
    @Schema(
        description = "Authenticated user identifier",
        example = "11111111-1111-1111-1111-111111111111")
    String userId;

    @Schema(description = "Principal username", example = "jdoe")
    String username;

    @Schema(description = "Role codes assigned to principal")
    List<String> roles;

    @Schema(description = "Permission codes assigned to principal")
    List<String> permissions;

    @Schema(description = "Workflow group identifiers assigned to principal")
    List<String> workflowGroupIds;

    @Schema(description = "Business client scope identifiers assigned to principal")
    List<String> clientScopeIds;
  }
}
