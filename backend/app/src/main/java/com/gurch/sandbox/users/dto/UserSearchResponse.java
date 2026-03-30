package com.gurch.sandbox.users.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Summary user DTO returned in user search results.
 *
 * @param id user identifier
 * @param username username
 * @param email email
 * @param active whether the user is active
 * @param tenantId optional tenant identifier
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param version optimistic lock version
 */
@Schema(description = "Summary user response")
public record UserSearchResponse(
    @Schema(description = "User identifier", example = "2") Integer id,
    @Schema(description = "Username", example = "jane.admin") String username,
    @Schema(description = "Email", example = "jane.admin@example.com") String email,
    @Schema(description = "Whether the user is active", example = "true") boolean active,
    @Schema(description = "Optional tenant identifier", example = "1") Integer tenantId,
    @Schema(description = "Creation timestamp") Instant createdAt,
    @Schema(description = "Last update timestamp") Instant updatedAt,
    @Schema(description = "Optimistic lock version", example = "0") Long version) {}
