package com.gurch.sandbox.workflows;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
  public List<WorkflowDefinitionResponse> search(WorkflowSearchCriteria criteria) {
    return workflowApi.searchProcessDefinitions(criteria);
  }

  /** Checks whether a process definition key exists. */
  @GetMapping("/{processDefinitionKey}/exists")
  public WorkflowDtos.ProcessDefinitionExistsResponse exists(
      @PathVariable String processDefinitionKey) {
    return new WorkflowDtos.ProcessDefinitionExistsResponse(
        workflowApi.processDefinitionExists(processDefinitionKey));
  }
}
