package com.gurch.sandbox.requesttypes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.AuditableEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Table("request_type_versions")
public class RequestTypeVersionEntity extends AuditableEntity {
  @Id private Long id;
  private Long requestTypeId;
  private Integer version;
  private String payloadHandlerId;
  private String processDefinitionKey;
  private JsonNode configJson;
}
