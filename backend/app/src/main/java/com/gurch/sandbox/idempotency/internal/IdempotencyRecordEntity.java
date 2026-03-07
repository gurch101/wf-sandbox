package com.gurch.sandbox.idempotency.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.persistence.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.relational.core.mapping.Table;

/** Entity for storing idempotency records. */
@Data
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@Table("api_idempotency_records")
public class IdempotencyRecordEntity extends BaseEntity<Long> {
  /** The idempotency key from the request header. */
  private String idempotencyKey;

  /** The operation identified by request method and path. */
  private String operation;

  /** The hash of the request payload. */
  private String requestHash;

  /** The current status of the request (PROCESSING or COMPLETED). */
  private IdempotencyStatus status;

  /** The HTTP response status code. */
  private Integer responseStatus;

  /** The HTTP response body stored as JSONB. */
  private JsonNode responseBody;
}
