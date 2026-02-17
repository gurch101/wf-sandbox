package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Summary response DTO for request search results.
 *
 * @param id unique identifier of the request
 * @param requestTypeKey request type key used for this request
 * @param requestTypeVersion resolved immutable request type version
 * @param status current status of the request
 * @param createdAt timestamp when the request was created
 * @param updatedAt timestamp when the request was last updated
 * @param version record version for optimistic locking
 */
@Schema(description = "Summary response representing a request in search results")
public record RequestSearchResponse(
    @Schema(description = "Unique identifier of the request", example = "123") Long id,
    @Schema(description = "Request type key used for this request", example = "loan")
        String requestTypeKey,
    @Schema(description = "Resolved immutable request type version", example = "2")
        Integer requestTypeVersion,
    @Schema(description = "Current status of the request", example = "IN_PROGRESS")
        RequestStatus status,
    @Schema(description = "Timestamp when the request was created") Instant createdAt,
    @Schema(description = "Timestamp when the request was last updated") Instant updatedAt,
    @Schema(description = "Version of the record for optimistic locking", example = "2")
        Long version) {}
