package com.gurch.sandbox.workflows;

import com.gurch.sandbox.dto.PagedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/internal/workflows/process-definitions")
@RequiredArgsConstructor
public class WorkflowController {

  private final WorkflowApi workflowApi;

  @GetMapping("/search")
  public PagedResponse<WorkflowDefinitionResponse> search(WorkflowSearchCriteria criteria) {
    return workflowApi.searchProcessDefinitions(criteria);
  }
}
