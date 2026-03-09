package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InputStream;
import lombok.Value;

/** Update command for document template metadata and optional content replacement. */
@Value
@Schema(description = "Update command for a document template")
public class DocumentTemplateUpdateRequest {
  @Schema(description = "Optional display name override", example = "Client Intake Form.pdf")
  String name;

  @Schema(description = "Optional file description", example = "Client onboarding template")
  String description;

  @Schema(description = "Optional replacement filename", example = "intake-form.pdf")
  String originalFilename;

  @Schema(description = "Optional replacement MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "Optional replacement content length in bytes")
  Long contentSize;

  @Schema(description = "Optional replacement input stream")
  InputStream contentStream;
}
