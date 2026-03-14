package com.gurch.sandbox.policies.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.policies.PolicyInputFieldDataType;
import com.gurch.sandbox.requesttypes.internal.RequestTypeEntity;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class PolicyModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private PolicyVersionRepository policyVersionRepository;
  @Autowired private PolicyInputCatalogRepository policyInputCatalogRepository;
  @Autowired private PolicyOutputContractRepository policyOutputContractRepository;

  @BeforeEach
  void setUp() {
    policyOutputContractRepository.deleteAll();
    policyInputCatalogRepository.deleteAll();
    policyVersionRepository.deleteAll();
    requestTypeRepository.deleteAll();
  }

  @Test
  void shouldReturnCapabilitiesForExplicitVersion() throws Exception {
    String requestTypeKey = uniqueTypeKey("WIRE_TRANSFER");
    Long requestTypeId = insertRequestType(requestTypeKey);
    Long policyVersionId = insertPolicyVersion(requestTypeId, 7, PolicyVersionStatus.DRAFT);
    insertInput(
        policyVersionId,
        "request.requestedAmount",
        "Requested Amount",
        PolicyInputSourceType.PAYLOAD,
        PolicyInputFieldDataType.NUMBER,
        true,
        "$.requestedAmount",
        null,
        objectMapper.nullNode(),
        null,
        objectMapper.readTree("[120000.0]"));
    insertInput(
        policyVersionId,
        "computed.accountType",
        "Account Type",
        PolicyInputSourceType.COMPUTABLE,
        PolicyInputFieldDataType.STRING,
        true,
        null,
        "account-profile-provider",
        objectMapper.readTree("[\"request.accountId\"]"),
        300,
        objectMapper.readTree("[\"BROKERAGE\"]"));
    insertOutputContract(
        policyVersionId,
        objectMapper.valueToTree(
            Map.of(
                "executionType",
                "HUMAN|COMPLETE",
                "then",
                Map.of("taskPlanPatch", Map.of("stages", "array"), "assignmentHints", "object"))));

    mockMvc
        .perform(get("/api/admin/policies/{policyId}/capabilities", policyVersionId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTypeId").value(requestTypeId))
        .andExpect(jsonPath("$.resolvedVersion").value(7))
        .andExpect(jsonPath("$.inputs.payloadFields[0].key").value("request.requestedAmount"))
        .andExpect(jsonPath("$.inputs.computableFields[0].key").value("computed.accountType"))
        .andExpect(
            jsonPath("$.inputs.computableFields[0].providerKey").value("account-profile-provider"))
        .andExpect(jsonPath("$.operatorsByType.NUMBER[0]").value("EQ"))
        .andExpect(jsonPath("$.assignmentStrategies[2]").value("BEST_USER_STUB"))
        .andExpect(jsonPath("$.supportedHitPolicies[0]").value("FIRST"))
        .andExpect(jsonPath("$.validationLimits.maxTreeDepth").value(5))
        .andExpect(jsonPath("$.outputSchema.executionType").value("HUMAN|COMPLETE"));
  }

  @Test
  void shouldReturnCapabilitiesForPublishedPolicyId() throws Exception {
    String requestTypeKey = uniqueTypeKey("WIRE_TRANSFER");
    Long requestTypeId = insertRequestType(requestTypeKey);
    Long publishedPolicy = insertPolicyVersion(requestTypeId, 5, PolicyVersionStatus.PUBLISHED);

    insertInput(
        publishedPolicy,
        "request.accountId",
        "Account Id",
        PolicyInputSourceType.PAYLOAD,
        PolicyInputFieldDataType.STRING,
        true,
        "$.accountId",
        null,
        objectMapper.nullNode(),
        null,
        objectMapper.readTree("[\"ACC-2\"]"));
    insertOutputContract(publishedPolicy, objectMapper.readTree("{\"executionType\":\"HUMAN\"}"));

    mockMvc
        .perform(get("/api/admin/policies/{policyId}/capabilities", publishedPolicy))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestTypeId").value(requestTypeId))
        .andExpect(jsonPath("$.resolvedVersion").value(5))
        .andExpect(jsonPath("$.outputSchema.executionType").value("HUMAN"));
  }

  @Test
  void shouldReturnNotFoundForUnknownPolicyId() throws Exception {
    mockMvc
        .perform(get("/api/admin/policies/{policyId}/capabilities", 999_999L))
        .andExpect(status().isNotFound());
  }

  private String uniqueTypeKey(String prefix) {
    return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
  }

  private Long insertRequestType(String typeKey) {
    return requestTypeRepository
        .save(
            RequestTypeEntity.builder()
                .typeKey(typeKey)
                .name(typeKey)
                .description("seeded for policy test")
                .active(true)
                .build())
        .getId();
  }

  private Long insertPolicyVersion(
      Long requestTypeId, int policyVersion, PolicyVersionStatus status) {
    return policyVersionRepository
        .save(
            PolicyVersionEntity.builder()
                .requestTypeId(requestTypeId)
                .policyVersion(policyVersion)
                .status(status)
                .build())
        .getId();
  }

  private void insertInput(
      Long policyVersionId,
      String fieldKey,
      String label,
      PolicyInputSourceType sourceType,
      PolicyInputFieldDataType dataType,
      boolean required,
      String path,
      String providerKey,
      JsonNode dependsOnJson,
      Integer freshnessSlaSeconds,
      JsonNode examplesJson) {
    policyInputCatalogRepository.save(
        PolicyInputCatalogEntity.builder()
            .policyVersionId(policyVersionId)
            .fieldKey(fieldKey)
            .label(label)
            .sourceType(sourceType)
            .dataType(dataType)
            .required(required)
            .path(path)
            .providerKey(providerKey)
            .dependsOnJson(dependsOnJson)
            .freshnessSlaSeconds(freshnessSlaSeconds)
            .examplesJson(examplesJson)
            .build());
  }

  private void insertOutputContract(Long policyVersionId, JsonNode outputSchemaJson) {
    policyOutputContractRepository.save(
        PolicyOutputContractEntity.builder()
            .policyVersionId(policyVersionId)
            .outputSchemaJson(outputSchemaJson)
            .build());
  }
}
