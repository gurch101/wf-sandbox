package com.gurch.sandbox.tenants;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Summary tenant DTO returned in tenant search results.
 *
 * @param id tenant identifier
 * @param name tenant name
 * @param active whether tenant is active
 * @param createdAt creation timestamp
 * @param updatedAt last update timestamp
 * @param version optimistic lock version
 */
@Schema(description = "Summary tenant response")
public record TenantSearchResponse(
    @Schema(description = "Tenant identifier", example = "1") Integer id,
    @Schema(description = "Tenant name", example = "acme") String name,
    @Schema(description = "Whether tenant is active", example = "true") boolean active,
    @Schema(description = "Creation timestamp") Instant createdAt,
    @Schema(description = "Last update timestamp") Instant updatedAt,
    @Schema(description = "Optimistic lock version", example = "0") Long version) {}
