package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Typed description of a single mergeable field discovered in a document template.
 *
 * @param key unique field key used during merge
 * @param type introspected field type
 * @param possibleValues allowed values for selectable controls
 */
@Schema(description = "Single parsed field inside a document template form map")
public record DocumentTemplateFormField(
    @Schema(description = "Field key", example = "clientName") String key,
    @Schema(description = "Field type", example = "TEXT") DocumentTemplateFormFieldType type,
    @Schema(description = "Possible values for selectable controls") List<String> possibleValues) {

  /** Canonical constructor that snapshots the possible values list. */
  public DocumentTemplateFormField {
    possibleValues = possibleValues == null ? List.of() : List.copyOf(possibleValues);
  }

  /** Returns an immutable copy of the possible values. */
  @Override
  public List<String> possibleValues() {
    return List.copyOf(possibleValues);
  }
}
