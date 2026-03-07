package com.gurch.sandbox.requests.activity.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Payload for task-assigned activity events.
 *
 * @param taskId request task identifier
 * @param taskEngineId workflow engine task identifier
 * @param taskName task display name
 * @param assignee task assignee when available
 */
@Schema(description = "Task assignment payload")
public record RequestTaskAssignedActivityPayload(
    @Schema(description = "Request task identifier", example = "5") Long taskId,
    @Schema(description = "Workflow task identifier", example = "9b20f7d4") String taskEngineId,
    @Schema(description = "Task display name", example = "Approve Request") String taskName,
    @Schema(description = "Task assignee", example = "demo") String assignee)
    implements RequestActivityPayload {}
