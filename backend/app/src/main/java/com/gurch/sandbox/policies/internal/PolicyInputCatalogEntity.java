package com.gurch.sandbox.policies.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.MutableEntity;
import com.gurch.sandbox.policies.PolicyInputFieldDataType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("policy_input_catalog")
class PolicyInputCatalogEntity extends MutableEntity<Long> {
  private Long policyVersionId;
  private String fieldKey;
  private String label;
  private PolicyInputSourceType sourceType;
  private PolicyInputFieldDataType dataType;
  private boolean required;
  private String path;
  private String providerKey;
  private JsonNode dependsOnJson;
  private Integer freshnessSlaSeconds;
  private JsonNode examplesJson;
}
