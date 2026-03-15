package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.requesttypes.InputFieldDataType;
import com.gurch.sandbox.requesttypes.ModelerInputProvider;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesResponse.ModelerInputField;
import java.util.List;
import org.springframework.stereotype.Component;

/** Shared workflow/task-result inputs available to all request type modeler sessions. */
@Component
class WorkflowTaskResultsModelerInputProvider implements ModelerInputProvider {

  private static final String PROVIDER_KEY = "workflow-task-results";

  @Override
  public boolean supports(RequestTypeEntity requestType, RequestTypeVersionEntity version) {
    return true;
  }

  @Override
  public List<ModelerInputField> workflowFields() {
    return List.of(
        new ModelerInputField(
            "workflow.lastCompletedTaskKey",
            "Last Completed Task Key",
            InputFieldDataType.STRING,
            false,
            null,
            PROVIDER_KEY,
            List.of(),
            List.of(),
            List.of("review_request"),
            "Stable key for the most recently completed user task."),
        new ModelerInputField(
            "workflow.lastCompletedTaskAction",
            "Last Completed Task Action",
            InputFieldDataType.STRING,
            false,
            null,
            PROVIDER_KEY,
            List.of(),
            List.of(),
            List.of("APPROVE", "REJECT"),
            "Action key chosen when the most recent user task was completed."));
  }
}
