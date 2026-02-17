package com.gurch.sandbox.idempotency.internal;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/** Entity for storing idempotency records. */
@Data
@Builder(toBuilder = true)
@Table("api_idempotency_records")
public class IdempotencyRecordEntity {
  /** The internal ID of the record. */
  @Id private Long id;

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

  /** The timestamp when the record was created. */
  @CreatedDate private Instant createdAt;
}
