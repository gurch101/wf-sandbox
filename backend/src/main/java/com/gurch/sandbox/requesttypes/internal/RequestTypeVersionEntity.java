package com.gurch.sandbox.requesttypes.internal;

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
@Table("request_type_versions")
public class RequestTypeVersionEntity extends MutableEntity<Long> {
  private Long requestTypeId;
  private Integer version;
  private String payloadHandlerId;
  private String processDefinitionKey;
  private JsonNode configJson;
}
