package com.gurch.sandbox.workflows;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.workflows.dto.WorkflowDefinitionResponse;
import com.gurch.sandbox.workflows.dto.WorkflowSearchCriteria;

/** API for workflow process-definition discovery and validation. */
public interface WorkflowApi {

  /**
   * Returns true when a process definition with the provided key is deployed.
   *
   * @param processDefinitionKey process definition key
   * @return whether the key exists
   */
  boolean processDefinitionExists(String processDefinitionKey);

  /**
   * Searches deployed process definitions by optional key pattern.
   *
   * @param criteria search criteria
   * @return deployed process definitions
   */
  PagedResponse<WorkflowDefinitionResponse> searchProcessDefinitions(
      WorkflowSearchCriteria criteria);
}
