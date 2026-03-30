package com.gurch.sandbox.documenttemplates.dto;

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

/** Criteria object for filtering uploaded document templates. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching document-template metadata")
public class DocumentTemplateSearchCriteria extends SearchCriteria {
  @Schema(
      description = "English or French name prefix match (case-insensitive)",
      example = "intake")
  private String nameBegins;

  @Schema(description = "Optional tenant filter. Null returns all templates")
  private Integer tenantId;

  public String getNamePattern() {
    return SearchCriteriaUtils.toUpperStartsWithPattern(nameBegins);
  }
}
