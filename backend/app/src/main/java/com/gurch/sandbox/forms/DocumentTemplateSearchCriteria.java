package com.gurch.sandbox.forms;

import com.gurch.sandbox.dto.SearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
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
  @Schema(description = "Partial name match (case-insensitive)", example = "intake")
  private String nameContains;

  @Schema(description = "Optional document type filters")
  private List<DocumentTemplateType> documentTypes;

  public String getNamePattern() {
    return Optional.ofNullable(nameContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.trim().toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }
}
