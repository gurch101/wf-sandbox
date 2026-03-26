package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Parsed form metadata discovered from an uploaded document template.
 *
 * @param fields discovered template fields
 */
@Schema(description = "Parsed form map for a document template")
public record DocumentTemplateFormMap(
    @Schema(description = "Discovered template fields") List<DocumentTemplateFormField> fields) {

  /** Canonical constructor that snapshots the discovered fields list. */
  public DocumentTemplateFormMap {
    fields = fields == null ? List.of() : List.copyOf(fields);
  }

  /** Returns an immutable copy of the discovered fields. */
  @Override
  public List<DocumentTemplateFormField> fields() {
    return List.copyOf(fields);
  }
}
