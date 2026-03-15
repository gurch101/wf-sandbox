package com.gurch.sandbox.policies;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Response contract for admin policy rule-authoring capabilities.
 *
 * @param requestTypeId request type id
 * @param resolvedVersion resolved policy version used for capability resolution
 * @param inputs available payload and computable inputs
 * @param operatorsByType supported operators by field data type
 * @param outputSchema required output contract schema
 * @param assignmentStrategies supported assignment strategy values
 * @param assignmentModes supported task-level assignment mode values
 * @param availableEscalationPolicies supported escalation policy catalog entries
 * @param reasonCodeCatalog supported task reason code catalog entries
 * @param validationLimits validation constraints for condition trees and rule sets
 * @param supportedHitPolicies supported DMN/rule hit policy values
 */
@Schema(description = "Admin policy capabilities for frontend rule authoring")
public record PolicyCapabilitiesResponse(
    @Schema(description = "Request type id", example = "42") Long requestTypeId,
    @Schema(description = "Resolved policy version", example = "7") Integer resolvedVersion,
    @Schema(description = "Input field catalog") Inputs inputs,
    @Schema(description = "Supported operators grouped by input data type")
        Map<String, List<String>> operatorsByType,
    @Schema(description = "Required output schema for policy then/outcome mappings")
        JsonNode outputSchema,
    @Schema(description = "Supported assignment strategy values") List<String> assignmentStrategies,
    @Schema(description = "Supported task-level assignment mode values")
        List<String> assignmentModes,
    @Schema(description = "Supported escalation policy catalog entries")
        List<EscalationPolicyOption> availableEscalationPolicies,
    @Schema(description = "Supported task reason code catalog entries")
        List<ReasonCodeOption> reasonCodeCatalog,
    @Schema(description = "Rule validation limits") ValidationLimits validationLimits,
    @Schema(description = "Supported rule set hit policies")
        List<PolicyHitPolicy> supportedHitPolicies) {

  /** Normalizes mutable collections into immutable defensive copies. */
  public PolicyCapabilitiesResponse {
    Objects.requireNonNull(inputs, "inputs is required");
    operatorsByType = immutableOperatorsByType(operatorsByType);
    assignmentStrategies = List.copyOf(Objects.requireNonNull(assignmentStrategies));
    assignmentModes = List.copyOf(Objects.requireNonNull(assignmentModes));
    availableEscalationPolicies = List.copyOf(Objects.requireNonNull(availableEscalationPolicies));
    reasonCodeCatalog = List.copyOf(Objects.requireNonNull(reasonCodeCatalog));
    Objects.requireNonNull(validationLimits, "validationLimits is required");
    supportedHitPolicies = List.copyOf(Objects.requireNonNull(supportedHitPolicies));
  }

  @Override
  public Map<String, List<String>> operatorsByType() {
    return immutableOperatorsByType(operatorsByType);
  }

  @Override
  public List<String> assignmentStrategies() {
    return List.copyOf(assignmentStrategies);
  }

  @Override
  public List<String> assignmentModes() {
    return List.copyOf(assignmentModes);
  }

  @Override
  public List<EscalationPolicyOption> availableEscalationPolicies() {
    return List.copyOf(availableEscalationPolicies);
  }

  @Override
  public List<ReasonCodeOption> reasonCodeCatalog() {
    return List.copyOf(reasonCodeCatalog);
  }

  @Override
  public List<PolicyHitPolicy> supportedHitPolicies() {
    return List.copyOf(supportedHitPolicies);
  }

  /**
   * Grouped input fields used by the frontend rule builder.
   *
   * @param payloadFields fields directly extracted from request payload
   * @param computableFields fields computed by enrichment providers
   */
  @Schema(description = "Payload and computable fields available for IF conditions")
  public record Inputs(
      @Schema(description = "Fields directly extracted from request payload")
          List<InputField> payloadFields,
      @Schema(description = "Fields computed by enrichment providers")
          List<InputField> computableFields) {

    /** Normalizes mutable collections into immutable defensive copies. */
    public Inputs {
      payloadFields = List.copyOf(Objects.requireNonNull(payloadFields));
      computableFields = List.copyOf(Objects.requireNonNull(computableFields));
    }

    @Override
    public List<InputField> payloadFields() {
      return List.copyOf(payloadFields);
    }

    @Override
    public List<InputField> computableFields() {
      return List.copyOf(computableFields);
    }
  }

  /**
   * One input field metadata descriptor.
   *
   * @param key stable field key
   * @param label human-readable field label
   * @param type field data type
   * @param required whether the field is required
   * @param path JSONPath for payload field
   * @param providerKey computable provider identifier
   * @param dependsOn dependency field keys for computable fields
   * @param freshnessSlaSeconds freshness SLA in seconds for computable fields
   * @param examples example values shown in admin UI
   */
  @Schema(description = "Input field descriptor")
  public record InputField(
      @Schema(description = "Stable field key", example = "request.requestedAmount") String key,
      @Schema(description = "Human-readable field label", example = "Requested Amount")
          String label,
      @Schema(description = "Field data type", example = "NUMBER") PolicyInputFieldDataType type,
      @Schema(description = "Whether field is required", example = "true") boolean required,
      @Schema(description = "JSONPath for payload field", example = "$.requestedAmount")
          String path,
      @Schema(description = "Computable provider key", example = "account-profile-provider")
          String providerKey,
      @Schema(description = "Field dependencies for computable fields") List<String> dependsOn,
      @Schema(description = "Freshness SLA in seconds for computable fields", example = "300")
          Integer freshnessSlaSeconds,
      @Schema(description = "Example values for UI hints") List<Object> examples) {

    /** Normalizes mutable collections into immutable defensive copies. */
    public InputField {
      dependsOn = List.copyOf(Objects.requireNonNull(dependsOn));
      examples = List.copyOf(Objects.requireNonNull(examples));
    }

    @Override
    public List<String> dependsOn() {
      return List.copyOf(dependsOn);
    }

    @Override
    public List<Object> examples() {
      return List.copyOf(examples);
    }
  }

  /**
   * Validation limits enforced by rule-set validation.
   *
   * @param maxTreeDepth maximum condition tree depth
   * @param maxNodesPerRule maximum total nodes per rule
   * @param maxRulesPerSet maximum allowed rules in one rule set
   */
  @Schema(description = "Validation limits enforced by rule-set APIs")
  public record ValidationLimits(
      @Schema(description = "Maximum condition tree depth", example = "5") int maxTreeDepth,
      @Schema(description = "Maximum total nodes per rule", example = "100") int maxNodesPerRule,
      @Schema(description = "Maximum rules allowed in one rule set", example = "500")
          int maxRulesPerSet) {}

  /**
   * One escalation policy catalog option exposed to the admin UI.
   *
   * @param key stable escalation policy key
   * @param label human-readable escalation policy label
   */
  @Schema(description = "Escalation policy catalog option")
  public record EscalationPolicyOption(
      @Schema(
              description = "Stable escalation policy key",
              example = "sla-breach-manager-escalation")
          String key,
      @Schema(
              description = "Human-readable escalation policy label",
              example = "Manager escalation on SLA breach")
          String label) {}

  /**
   * One reason code catalog option exposed to the admin UI.
   *
   * @param code stable reason code value
   * @param label human-readable reason code label
   */
  @Schema(description = "Reason code catalog option")
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
