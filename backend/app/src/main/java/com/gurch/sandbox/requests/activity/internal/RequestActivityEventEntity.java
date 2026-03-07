package com.gurch.sandbox.requests.activity.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.BaseEntity;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("request_activity_events")
public class RequestActivityEventEntity extends BaseEntity<Long> {
  private Long requestId;
  private RequestActivityEventType eventType;
  private Integer actorUserId;
  private String correlationId;
  private JsonNode payload;
}
