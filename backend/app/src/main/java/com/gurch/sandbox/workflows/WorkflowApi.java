package com.gurch.sandbox.workflows;

import com.gurch.sandbox.dto.PagedResponse;

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
   * Deploys a BPMN model and returns the deployed process definition key.
   *
   * @param resourceName deployment resource name
   * @param bpmnXml BPMN XML content
   * @return deployed process definition key
   */
  String deployBpmnModel(String resourceName, String bpmnXml);

  /**
   * Searches deployed process definitions by optional key pattern.
   *
   * @param criteria search criteria
   * @return deployed process definitions
   */
  PagedResponse<WorkflowDefinitionResponse> searchProcessDefinitions(
      WorkflowSearchCriteria criteria);
}
