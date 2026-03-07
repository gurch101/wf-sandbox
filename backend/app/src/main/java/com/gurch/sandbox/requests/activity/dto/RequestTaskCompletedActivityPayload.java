package com.gurch.sandbox.requests.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for task-completed activity events.
 *
 * @param taskId request task identifier
 * @param taskEngineId workflow engine task identifier
 * @param taskName task display name
 * @param assignee task assignee when available
 * @param action selected task action
 */
@Schema(description = "Task completion payload")
public record RequestTaskCompletedActivityPayload(
    @Schema(description = "Request task identifier", example = "5") Long taskId,
    @Schema(description = "Workflow task identifier", example = "9b20f7d4") String taskEngineId,
    @Schema(description = "Task display name", example = "Approve Request") String taskName,
    @Schema(description = "Task assignee", example = "demo") String assignee,
    @Schema(description = "Task completion action", example = "APPROVED") String action)
    implements RequestActivityPayload {}
