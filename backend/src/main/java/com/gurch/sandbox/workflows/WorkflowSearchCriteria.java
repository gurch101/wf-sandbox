package com.gurch.sandbox.workflows;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Search criteria for deployed workflow process definitions. */
@Value
@Builder
@Jacksonized
@Schema(description = "Workflow process-definition search criteria")
public class WorkflowSearchCriteria {
  @Schema(description = "Case-insensitive substring filter for process key", example = "request")
  String processDefinitionKeyContains;

  /**
   * Returns an uppercase wildcard pattern for process key filtering.
   *
   * @return uppercase pattern for LIKE query, or null when filter is absent
   */
  public String getProcessDefinitionKeyPattern() {
    return Optional.ofNullable(processDefinitionKeyContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s + "%")
        .orElse(null);
  }
}
