package com.gurch.sandbox.tenants;

import com.gurch.sandbox.dto.SearchCriteriaUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Criteria object for searching tenants. */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching tenants")
public class TenantSearchCriteria {
  @Schema(description = "Case-insensitive tenant name filter", example = "acm")
  String nameContains;

  @Schema(description = "Filter by active flag", example = "true")
  Boolean active;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Page size", example = "20")
  Integer size;

  public String getNamePattern() {
    return SearchCriteriaUtils.toUpperLikePattern(nameContains);
  }
}
