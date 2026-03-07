package com.gurch.sandbox.audit.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("audit_log_events")
public class AuditLogEventEntity extends BaseEntity<Long> {
  private String resourceType;
  private String resourceId;
  private AuditLogAction action;
  private Integer actorUserId;
  private Integer tenantId;
  private String correlationId;
  private JsonNode beforeState;
  private JsonNode afterState;
}
