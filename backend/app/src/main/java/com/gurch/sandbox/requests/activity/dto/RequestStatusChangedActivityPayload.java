package com.gurch.sandbox.requests.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for request status transition activity events.
 *
 * @param fromStatus previous status
 * @param toStatus new status
 * @param taskId related request task id when applicable
 * @param processInstanceId related workflow process instance id when applicable
 */
@Schema(description = "Status transition payload")
public record RequestStatusChangedActivityPayload(
    @Schema(description = "Previous request status", example = "SUBMITTED") String fromStatus,
    @Schema(description = "Current request status", example = "IN_PROGRESS") String toStatus,
    @Schema(description = "Related request task id when available", example = "10") Long taskId,
    @Schema(description = "Related workflow process instance id when available")
        String processInstanceId)
    implements RequestActivityPayload {}
