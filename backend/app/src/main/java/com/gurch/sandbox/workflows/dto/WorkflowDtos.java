package com.gurch.sandbox.workflows.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** DTOs for workflow internal endpoints. */
public interface WorkflowDtos {

  /** Boolean response for process-definition existence checks. */
  @Value
  @Schema(description = "Process-definition existence response")
  class ProcessDefinitionExistsResponse {
    @Schema(description = "Whether process definition exists", example = "true")
    boolean exists;
  }
}
