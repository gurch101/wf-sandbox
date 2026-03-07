package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.dto.SearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** Search filters for request type internal API. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching request types")
public class RequestTypeSearchCriteria extends SearchCriteria {
  @Schema(description = "Case-insensitive substring filter for type key", example = "loan")
  private String typeKeyContains;

  @Schema(description = "Filter by active flag", example = "true")
  private Boolean active;

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
