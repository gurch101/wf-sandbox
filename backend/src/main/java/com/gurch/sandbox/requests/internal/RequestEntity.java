package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.VersionedAuditableEntity;
import com.gurch.sandbox.requests.RequestStatus;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@Table("requests")
public class RequestEntity extends VersionedAuditableEntity<Long> {
  private String requestTypeKey;
  private Integer requestTypeVersion;
  private JsonNode payloadJson;
  private RequestStatus status;
  private String processInstanceId;
  private String workflowGroupCode;

  @Builder.Default private String businessClientId = "CLIENT_A";
}
