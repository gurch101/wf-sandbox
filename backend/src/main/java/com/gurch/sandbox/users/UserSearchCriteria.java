package com.gurch.sandbox.users;

import com.gurch.sandbox.dto.SearchCriteriaUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Criteria object for searching users. */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching users")
public class UserSearchCriteria {
  @Schema(description = "Case-insensitive username filter", example = "admin")
  String usernameContains;

  @Schema(description = "Case-insensitive email filter", example = "example.com")
  String emailContains;

  @Schema(description = "Filter by active flag", example = "true")
  Boolean active;

  @Schema(description = "Filter by tenant identifier", example = "1")
  Integer tenantId;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Page size", example = "20")
  Integer size;

  public String getUsernamePattern() {
    return SearchCriteriaUtils.toUpperLikePattern(usernameContains);
  }

  public String getEmailPattern() {
    return SearchCriteriaUtils.toUpperLikePattern(emailContains);
  }
}
