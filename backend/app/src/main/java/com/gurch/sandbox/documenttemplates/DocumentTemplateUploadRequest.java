package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.InputStream;
import lombok.Value;

/** Upload command used by the forms module service. */
@Value
@Schema(description = "Upload command for a document template")
public class DocumentTemplateUploadRequest {
  @Schema(
      description = "Optional stable template key used for request-driven document generation",
      example = "loan-cover-letter")
  String templateKey;

  @Schema(description = "Optional display name override", example = "Client Intake Form.pdf")
  String name;

  @Schema(description = "Optional file description", example = "Client onboarding template")
  String description;

  @Schema(description = "Optional tenant identifier; null means global template", example = "1")
  Integer tenantId;

  @Schema(description = "Original filename from multipart upload", example = "intake-form.pdf")
  String originalFilename;

  @Schema(description = "Detected or declared MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "Uploaded content length in bytes")
  Long contentSize;

  @Schema(description = "Input stream for uploaded content")
  InputStream contentStream;
}
