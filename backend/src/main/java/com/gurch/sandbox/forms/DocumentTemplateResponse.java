package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.Builder;
import lombok.Value;

/** Response representing persisted metadata for an uploaded document template. */
@Value
@Builder
@Schema(description = "Response representing document-template metadata")
public class DocumentTemplateResponse {
  @Schema(description = "Unique identifier of the file", example = "123")
  Long id;

  @Schema(description = "Display name for the file", example = "Client Intake Form.pdf")
  String name;

  @Schema(description = "Optional user-provided description", example = "Q1 onboarding packet")
  String description;

  @Schema(description = "MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "Stored file size in bytes", example = "82944")
  Long contentSize;

  @Schema(description = "SHA-256 checksum of uploaded content")
  String checksumSha256;

  @Schema(description = "Document type classification")
  DocumentTemplateType documentType;

  @Schema(description = "Timestamp when this record was created")
  Instant createdAt;

  @Schema(description = "Timestamp when this record was last updated")
  Instant updatedAt;

  @Schema(description = "Optimistic-lock version", example = "0")
  Long version;
}
