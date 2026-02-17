package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/** Criteria object for filtering uploaded form files. */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching form-file metadata")
public class FormFileSearchCriteria {
  @Schema(description = "Partial name match (case-insensitive)", example = "intake")
  String nameContains;

  @Schema(description = "Partial MIME type match (case-insensitive)", example = "pdf")
  String mimeTypeContains;

  @Schema(description = "Optional document type filters")
  List<FormDocumentType> documentTypes;

  @Schema(description = "Optional signature status filters")
  List<FormSignatureStatus> signatureStatuses;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Page size", example = "20")
  Integer size;

  public String getNamePattern() {
    return Optional.ofNullable(nameContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.trim().toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }

  public String getMimeTypePattern() {
    return Optional.ofNullable(mimeTypeContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.trim().toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }
}
