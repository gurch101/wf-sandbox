package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Criteria object for searching request records. Supports filtering by name, status, and ID, as
 * well as pagination.
 */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching request records")
public class RequestSearchCriteria {
  @Schema(description = "Partial name to search for (case-insensitive)", example = "feature")
  String nameContains;

  @Schema(description = "List of statuses to filter by", example = "[\"DRAFT\", \"IN_PROGRESS\"]")
  List<RequestStatus> statuses;

  @Schema(description = "List of specific IDs to filter by", example = "[1, 2, 3]")
  List<Long> ids;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Number of records per page", example = "20")
  Integer size;

  public String getNamePattern() {
    return Optional.ofNullable(nameContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }
}
