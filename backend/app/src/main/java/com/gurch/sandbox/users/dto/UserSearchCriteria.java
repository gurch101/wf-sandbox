package com.gurch.sandbox.users.dto;

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

/** Criteria object for searching users. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching users")
public class UserSearchCriteria extends SearchCriteria {
  @Schema(description = "Case-insensitive username filter", example = "admin")
  private String usernameContains;

  @Schema(description = "Case-insensitive email filter", example = "example.com")
  private String emailContains;

  @Schema(description = "Filter by active flag", example = "true")
  private Boolean active;

  @Schema(description = "Filter by tenant identifier", example = "1")
  private Integer tenantId;

  public String getUsernamePattern() {
    return SearchCriteriaUtils.toUpperLikePattern(usernameContains);
  }

  public String getEmailPattern() {
    return SearchCriteriaUtils.toUpperLikePattern(emailContains);
  }
}
