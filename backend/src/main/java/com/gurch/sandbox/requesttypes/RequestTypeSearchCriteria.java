package com.gurch.sandbox.requesttypes;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Search filters for request type internal API. */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching request types")
public class RequestTypeSearchCriteria {
  @Schema(description = "Case-insensitive substring filter for type key", example = "loan")
  String typeKeyContains;

  @Schema(description = "Filter by active flag", example = "true")
  Boolean active;

  /**
   * Returns an uppercase wildcard pattern for type key filtering.
   *
   * @return uppercase pattern for SQL LIKE, or null when filter is absent
   */
  public String getTypeKeyPattern() {
    return Optional.ofNullable(typeKeyContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }
}
