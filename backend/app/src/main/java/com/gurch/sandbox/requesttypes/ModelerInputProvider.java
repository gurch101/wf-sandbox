package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.requesttypes.internal.RequestTypeEntity;
import com.gurch.sandbox.requesttypes.internal.RequestTypeVersionEntity;
import java.util.List;

/** Reusable code-defined provider of non-payload modeler inputs. */
public interface ModelerInputProvider {

  /**
   * Whether this provider should contribute inputs for the provided request type version.
   *
   * @param requestType request type aggregate
   * @param version resolved request type version
   * @return whether this provider applies
   */
  boolean supports(RequestTypeEntity requestType, RequestTypeVersionEntity version);

  /**
   * Computed fields contributed by this provider.
   *
   * @return computed fields
   */
  default List<RequestTypeModelerCapabilitiesResponse.ModelerInputField> computedFields() {
    return List.of();
  }

  /**
   * Workflow/runtime fields contributed by this provider.
   *
   * @return workflow fields
   */
  default List<RequestTypeModelerCapabilitiesResponse.ModelerInputField> workflowFields() {
    return List.of();
  }
}
