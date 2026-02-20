package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Criteria object for searching workflow tasks linked to requests. */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching request tasks")
public class RequestTaskSearchCriteria {
  @Schema(description = "Filter by request identifier", example = "123")
  Long requestId;

  @Schema(description = "Filter by assignee (case-insensitive)", example = "demo")
  String assignee;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Number of records per page", example = "20")
  Integer size;

  /** Returns normalized uppercase assignee for case-insensitive filtering. */
  public String getNormalizedAssignee() {
    return Optional.ofNullable(assignee)
        .filter(value -> !value.isBlank())
        .map(value -> value.trim().toUpperCase(Locale.ROOT))
        .orElse(null);
  }
}
