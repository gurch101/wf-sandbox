package com.gurch.sandbox.requests.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.requests.RequestStatus;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.annotation.Version;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder(toBuilder = true)
@Table("requests")
public class RequestEntity {
  @Id private Long id;
  private String requestTypeKey;
  private Integer requestTypeVersion;
  private JsonNode payloadJson;
  private RequestStatus status;
  private String processInstanceId;
  @CreatedDate private Instant createdAt;
  @LastModifiedDate private Instant updatedAt;
  @Version private Long version;
}
