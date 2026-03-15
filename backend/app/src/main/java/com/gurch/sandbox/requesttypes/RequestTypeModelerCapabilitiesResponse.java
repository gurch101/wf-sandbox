package com.gurch.sandbox.requesttypes;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Modeler capability contract for a specific request type version.
 *
 * @param typeKey request type key
 * @param version resolved immutable request type version
 * @param inputs available request/modeler inputs
 * @param operatorsByType supported operators grouped by input data type
 * @param assignmentModes global assignment mode values
 * @param availableEscalationHandlers backend-supported escalation handlers
 * @param reasonCodeCatalog configured reason code catalog entries
 */
@Schema(description = "Modeler capabilities for a request type version")
public record RequestTypeModelerCapabilitiesResponse(
    @Schema(description = "Request type key", example = "loan") String typeKey,
    @Schema(description = "Resolved immutable request type version", example = "1") Integer version,
    @Schema(description = "Available request/modeler inputs") Inputs inputs,
    @Schema(description = "Supported operators grouped by input data type")
        Map<String, List<String>> operatorsByType,
    @Schema(description = "Global assignment mode values") List<AssignmentMode> assignmentModes,
    @Schema(description = "Backend-supported escalation handlers")
        List<EscalationHandler> availableEscalationHandlers,
    @Schema(description = "Configured reason code catalog entries")
        List<ReasonCodeOption> reasonCodeCatalog) {

  /** Normalizes mutable collections into immutable defensive copies. */
  public RequestTypeModelerCapabilitiesResponse {
    Objects.requireNonNull(typeKey, "typeKey is required");
    Objects.requireNonNull(version, "version is required");
    Objects.requireNonNull(inputs, "inputs is required");
    operatorsByType = immutableOperatorsByType(operatorsByType);
    assignmentModes = List.copyOf(Objects.requireNonNull(assignmentModes));
    availableEscalationHandlers = List.copyOf(Objects.requireNonNull(availableEscalationHandlers));
    reasonCodeCatalog = List.copyOf(Objects.requireNonNull(reasonCodeCatalog));
  }

  @Override
  public Map<String, List<String>> operatorsByType() {
    return immutableOperatorsByType(operatorsByType);
  }

  @Override
  public List<AssignmentMode> assignmentModes() {
    return List.copyOf(assignmentModes);
  }

  @Override
  public List<EscalationHandler> availableEscalationHandlers() {
    return List.copyOf(availableEscalationHandlers);
  }

  @Override
  public List<ReasonCodeOption> reasonCodeCatalog() {
    return List.copyOf(reasonCodeCatalog);
  }

  /**
   * Grouped payload/computed/workflow inputs available to the modeler.
   *
   * @param payloadFields payload fields inferred from the request payload contract
   * @param computedFields computed fields derived from provider bundles
   * @param workflowFields workflow/runtime fields available for branching
   */
  @Schema(description = "Grouped input universe for the modeler")
  public record Inputs(
      @Schema(description = "Payload fields inferred from the request payload contract")
          List<ModelerInputField> payloadFields,
      @Schema(description = "Computed fields derived from provider bundles")
          List<ModelerInputField> computedFields,
      @Schema(description = "Workflow/runtime fields available for branching")
          List<ModelerInputField> workflowFields) {

    /** Normalizes grouped input collections into immutable defensive copies. */
    public Inputs {
      payloadFields = List.copyOf(Objects.requireNonNull(payloadFields));
      computedFields = List.copyOf(Objects.requireNonNull(computedFields));
      workflowFields = List.copyOf(Objects.requireNonNull(workflowFields));
    }
  }

  /**
   * One modeler-visible input field.
   *
   * @param key stable field key
   * @param label human-readable field label
   * @param type field data type
   * @param required whether the field is required
   * @param path JSONPath for payload fields
   * @param providerKey provider key for computed/workflow fields
   * @param dependsOn dependency keys for computed/workflow fields
   * @param enumValues allowed enum values where applicable
   * @param examples example values for authoring hints
   * @param description field description shown in the modeler
   */
  @Schema(description = "Modeler input field descriptor")
  public record ModelerInputField(
      @Schema(description = "Stable field key", example = "request.amount") String key,
      @Schema(description = "Human-readable field label", example = "Amount") String label,
      @Schema(description = "Field data type", example = "NUMBER") InputFieldDataType type,
      @Schema(description = "Whether field is required", example = "true") boolean required,
      @Schema(description = "JSONPath for payload fields", example = "$.amount") String path,
      @Schema(
              description = "Provider key for computed/workflow fields",
              example = "workflow-task-results")
          String providerKey,
      @Schema(description = "Dependency keys for computed/workflow fields") List<String> dependsOn,
      @Schema(description = "Allowed enum values where applicable") List<String> enumValues,
      @Schema(description = "Example values for authoring hints") List<Object> examples,
      @Schema(description = "Field description shown in the modeler") String description) {

    /** Normalizes list-valued field metadata into immutable defensive copies. */
    public ModelerInputField {
      dependsOn = List.copyOf(Objects.requireNonNull(dependsOn));
      enumValues = List.copyOf(Objects.requireNonNull(enumValues));
      examples = List.copyOf(Objects.requireNonNull(examples));
    }
  }

  /**
   * One reason code catalog option.
   *
   * @param code stable reason code value
   * @param label human-readable reason code label
   */
  @Schema(description = "Reason code option")
  public record ReasonCodeOption(
      @Schema(description = "Stable reason code value", example = "HIGH_RISK_AMOUNT") String code,
      @Schema(description = "Human-readable reason code label", example = "High risk amount")
          String label) {}

  private static Map<String, List<String>> immutableOperatorsByType(
      Map<String, List<String>> operatorsByType) {
    Objects.requireNonNull(operatorsByType, "operatorsByType is required");

    Map<String, List<String>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<String>> entry : operatorsByType.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue()));
    }
    return Map.copyOf(copy);
  }
}
