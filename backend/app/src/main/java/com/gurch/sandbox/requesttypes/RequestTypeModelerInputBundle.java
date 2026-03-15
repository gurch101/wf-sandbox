package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.requesttypes.internal.RequestTypeEntity;
import com.gurch.sandbox.requesttypes.internal.RequestTypeVersionEntity;
import java.util.List;

/** Reusable code-defined bundle of modeler inputs. */
public interface RequestTypeModelerInputBundle {

  /**
   * Whether this bundle should contribute inputs for the provided request type version.
   *
   * @param requestType request type aggregate
   * @param version resolved request type version
   * @return whether this bundle applies
   */
  boolean supports(RequestTypeEntity requestType, RequestTypeVersionEntity version);

  /**
   * Computed fields contributed by this bundle.
   *
   * @return computed fields
   */
  default List<RequestTypeModelerCapabilitiesResponse.ModelerInputField> computedFields() {
    return List.of();
  }

  /**
   * Workflow/runtime fields contributed by this bundle.
   *
   * @return workflow fields
   */
  default List<RequestTypeModelerCapabilitiesResponse.ModelerInputField> workflowFields() {
    return List.of();
  }
}
