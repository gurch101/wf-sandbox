package com.gurch.sandbox.policies.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.MutableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("policy_output_contracts")
class PolicyOutputContractEntity extends MutableEntity<Long> {
  private Long policyVersionId;
  private JsonNode outputSchemaJson;
}
