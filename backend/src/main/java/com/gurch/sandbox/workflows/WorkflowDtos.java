package com.gurch.sandbox.workflows;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
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

  /** Response wrapper for process-definition search endpoint. */
  @Value
  @Schema(description = "Response wrapper for workflow definition search results")
  class SearchResponse {
    @Schema(description = "Matching workflow definitions")
    List<WorkflowDefinitionResponse> workflowDefinitions;
  }
}
