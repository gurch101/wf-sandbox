package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.MutableEntity;
import com.gurch.sandbox.requests.RequestStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("requests")
public class RequestEntity extends MutableEntity<Long> {
  private String requestTypeKey;
  private Integer requestTypeVersion;
  private JsonNode payloadJson;
  private RequestStatus status;
  private String processInstanceId;
}
