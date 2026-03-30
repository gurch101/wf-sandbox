package com.gurch.sandbox.workflows.dto;

import com.gurch.sandbox.dto.SearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** Search criteria for deployed workflow process definitions. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Workflow process-definition search criteria")
public class WorkflowSearchCriteria extends SearchCriteria {
  @Schema(description = "Case-insensitive substring filter for process key", example = "request")
  private String processDefinitionKeyContains;

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
