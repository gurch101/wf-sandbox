package com.gurch.sandbox.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Value;

/** DTOs for admin user CRUD endpoints. */
public interface UserDtos {

  /** Request body for creating a user. */
  @Value
  @Schema(description = "Create user request")
  class CreateUserRequest {
    @NotBlank(message = "username is required")
    String username;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    String email;

    Boolean active;

    Integer tenantId;
  }

  /** Request body for updating a user. */
  @Value
  @Schema(description = "Update user request")
  class UpdateUserRequest {
    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    String email;

    Boolean active;

    Integer tenantId;

    @NotNull(message = "version is required")
    Long version;
  }
}
