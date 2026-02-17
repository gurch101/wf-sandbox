package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Value;

/** Upload command used by the forms module service. */
@Value
@Schema(description = "Upload command for a form file")
public class FormFileUploadRequest {
  @Schema(description = "Optional display name override", example = "Client Intake Form.pdf")
  String name;

  @Schema(description = "Optional file description", example = "Client onboarding template")
  String description;

  @Schema(description = "Original filename from multipart upload", example = "intake-form.pdf")
  String originalFilename;

  @Schema(description = "Detected or declared MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "Raw uploaded bytes")
  byte[] content;
}
