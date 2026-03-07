package com.gurch.sandbox.requests.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/**
 * Response row for request activity timeline entries.
 *
 * @param id activity event identifier
 * @param requestId request identifier
 * @param eventType domain event type
 * @param actorUserId user actor identifier when available
 * @param correlationId correlation identifier linking related operations
 * @param payload domain-specific event payload
 * @param createdAt timestamp when the activity event was recorded
 */
@Schema(description = "Activity event entry associated with a request")
public record RequestActivityEventResponse(
    @Schema(description = "Activity event identifier", example = "1001") Long id,
    @Schema(description = "Request identifier", example = "123") Long requestId,
    @Schema(description = "Domain activity event type", example = "STATUS_CHANGED")
        RequestActivityEventType eventType,
    @Schema(description = "User actor identifier when available", example = "1")
        Integer actorUserId,
    @Schema(description = "Correlation identifier linking related operations") String correlationId,
    @Schema(description = "Domain-specific typed event payload") RequestActivityPayload payload,
    @Schema(description = "Timestamp when the activity event was recorded") Instant createdAt) {}
