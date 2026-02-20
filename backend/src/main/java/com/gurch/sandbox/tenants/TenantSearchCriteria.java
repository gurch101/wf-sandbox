package com.gurch.sandbox.tenants;

import com.gurch.sandbox.dto.SearchCriteria;
import com.gurch.sandbox.dto.SearchCriteriaUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** Criteria object for searching tenants. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching tenants")
public class TenantSearchCriteria extends SearchCriteria {
  @Schema(description = "Case-insensitive tenant name filter", example = "acm")
  private String nameContains;

  @Schema(description = "Filter by active flag", example = "true")
  private Boolean active;

  public String getNamePattern() {
    return SearchCriteriaUtils.toUpperLikePattern(nameContains);
  }
}
