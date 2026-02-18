package com.gurch.sandbox.workflows;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal endpoints for discovering and validating workflow definitions. */
@RestController
@RequestMapping("/api/internal/workflows/process-definitions")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowApi workflowApi;

  /** Searches deployed process definitions. */
  @GetMapping("/search")
  public WorkflowDtos.SearchResponse search(WorkflowSearchCriteria criteria) {
    return new WorkflowDtos.SearchResponse(workflowApi.searchProcessDefinitions(criteria));
  }
}
