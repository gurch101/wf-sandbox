package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Value;

/** Response wrapper returned by document-template search endpoint. */
@Value
@Schema(description = "Response wrapper for document-template search")
public class DocumentTemplateSearchResponse {
  @Schema(description = "Matching document-template records")
  List<DocumentTemplateResponse> documentTemplates;
}
