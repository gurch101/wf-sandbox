package com.gurch.sandbox.policies.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.policies.PolicyAdminApi;
import com.gurch.sandbox.policies.PolicyCapabilitiesResponse;
import com.gurch.sandbox.policies.PolicyHitPolicy;
import com.gurch.sandbox.web.NotFoundException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultPolicyAdminService implements PolicyAdminApi {

  private static final int MAX_TREE_DEPTH = 5;
  private static final int MAX_NODES_PER_RULE = 100;
  private static final int MAX_RULES_PER_SET = 500;

  private static final List<String> ASSIGNMENT_STRATEGIES =
      List.of("STATIC", "POLICY_HINT", "BEST_USER_STUB");

  private static final List<String> ASSIGNMENT_MODES =
      List.of("CANDIDATE_USERS", "CANDIDATE_GROUPS", "DIRECT_ASSIGNEE", "UNASSIGNED");

  private static final List<PolicyCapabilitiesResponse.EscalationPolicyOption> ESCALATION_POLICIES =
      List.of(
          new PolicyCapabilitiesResponse.EscalationPolicyOption(
              "sla-breach-manager-escalation", "Manager escalation on SLA breach"));

  private static final List<PolicyCapabilitiesResponse.ReasonCodeOption> REASON_CODE_CATALOG =
      List.of(
          new PolicyCapabilitiesResponse.ReasonCodeOption("HIGH_RISK_AMOUNT", "High risk amount"),
          new PolicyCapabilitiesResponse.ReasonCodeOption(
              "MANUAL_REVIEW_REQUIRED", "Manual review required"));

  private static final List<PolicyHitPolicy> HIT_POLICIES =
      List.of(PolicyHitPolicy.FIRST, PolicyHitPolicy.COLLECT);

  private static final Map<String, List<String>> OPERATORS_BY_TYPE =
      Map.of(
          "STRING",
          List.of(
              "EQ",
              "NEQ",
              "IN",
              "NOT_IN",
              "CONTAINS",
              "STARTS_WITH",
              "ENDS_WITH",
              "IS_NULL",
              "IS_NOT_NULL"),
          "NUMBER",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "BOOLEAN",
          List.of("EQ", "NEQ", "IS_NULL", "IS_NOT_NULL"),
          "DATE",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "DATETIME",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "JSON",
          List.of("EQ", "NEQ", "CONTAINS", "IS_NULL", "IS_NOT_NULL"));

  private final PolicyVersionRepository policyVersionRepository;
  private final PolicyInputCatalogRepository policyInputCatalogRepository;
  private final PolicyOutputContractRepository policyOutputContractRepository;

  @Override
  @Transactional(readOnly = true)
  public PolicyCapabilitiesResponse getCapabilities(Long policyId) {
    PolicyVersionEntity policyVersion =
        policyVersionRepository
            .findById(policyId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "Policy version with id %d not found".formatted(policyId)));

    List<PolicyCapabilitiesResponse.InputField> payloadFields = new ArrayList<>();
    List<PolicyCapabilitiesResponse.InputField> computableFields = new ArrayList<>();

    List<PolicyInputCatalogEntity> inputRows =
        policyInputCatalogRepository.findAllByPolicyVersionIdOrderByFieldKey(policyVersion.getId());

    for (PolicyInputCatalogEntity row : inputRows) {
      PolicyCapabilitiesResponse.InputField field =
          new PolicyCapabilitiesResponse.InputField(
              row.getFieldKey(),
              row.getLabel(),
              row.getDataType(),
              row.isRequired(),
              row.getPath(),
              row.getProviderKey(),
              readStringList(row.getDependsOnJson()),
              row.getFreshnessSlaSeconds(),
              readObjectList(row.getExamplesJson()));
      if (PolicyInputSourceType.COMPUTABLE == row.getSourceType()) {
        computableFields.add(field);
      } else {
        payloadFields.add(field);
      }
    }

    JsonNode outputSchema =
        loadOutputSchema(
            policyVersion.getId(),
            policyVersion.getRequestTypeId(),
            policyVersion.getPolicyVersion());

    return new PolicyCapabilitiesResponse(
        policyVersion.getRequestTypeId(),
        policyVersion.getPolicyVersion(),
        new PolicyCapabilitiesResponse.Inputs(payloadFields, computableFields),
        new LinkedHashMap<>(OPERATORS_BY_TYPE),
        outputSchema,
        ASSIGNMENT_STRATEGIES,
        ASSIGNMENT_MODES,
        ESCALATION_POLICIES,
        REASON_CODE_CATALOG,
        new PolicyCapabilitiesResponse.ValidationLimits(
            MAX_TREE_DEPTH, MAX_NODES_PER_RULE, MAX_RULES_PER_SET),
        HIT_POLICIES);
  }

  private JsonNode loadOutputSchema(Long policyVersionId, Long requestTypeId, Integer version) {
    return policyOutputContractRepository
        .findByPolicyVersionId(policyVersionId)
        .map(PolicyOutputContractEntity::getOutputSchemaJson)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Output contract not found for request type id %d version %d"
                        .formatted(requestTypeId, version)));
  }

  private List<String> readStringList(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return List.of();
    }
    List<String> values = new ArrayList<>();
    for (JsonNode item : node) {
      values.add(item.asText());
    }
    return values;
  }

  private List<Object> readObjectList(JsonNode node) {
    if (node == null || node.isNull() || !node.isArray()) {
      return List.of();
    }
    List<Object> values = new ArrayList<>();
    for (JsonNode item : node) {
      if (item.isTextual()) {
        values.add(item.asText());
      } else if (item.isNumber()) {
        values.add(item.numberValue());
      } else if (item.isBoolean()) {
        values.add(item.booleanValue());
      } else if (item.isNull()) {
        values.add(null);
      } else {
        values.add(item);
      }
    }
    return values;
  }
}
