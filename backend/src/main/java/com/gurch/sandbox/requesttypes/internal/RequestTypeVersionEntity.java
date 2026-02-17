package com.gurch.sandbox.requesttypes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@Table("request_type_versions")
public class RequestTypeVersionEntity {
  @Id private Long id;
  private Long requestTypeId;
  private Integer version;
  private String payloadHandlerId;
  private String processDefinitionKey;
  private JsonNode configJson;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
}
