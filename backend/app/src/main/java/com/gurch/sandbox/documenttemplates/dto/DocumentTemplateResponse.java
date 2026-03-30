package com.gurch.sandbox.documenttemplates.dto;

import com.gurch.sandbox.documenttemplates.DocumentTemplateLanguage;
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

  @Schema(description = "English display name for the file", example = "Client Intake Form.pdf")
  String enName;

  @Schema(
      description = "French display name for the file",
      example = "Formulaire d'accueil client.pdf")
  String frName;

  @Schema(
      description = "Optional English user-provided description",
      example = "Q1 onboarding packet")
  String enDescription;

  @Schema(
      description = "Optional French user-provided description",
      example = "Dossier d'integration T1")
  String frDescription;

  @Schema(description = "MIME type", example = "application/pdf")
  String mimeType;

  @Schema(description = "Stored file size in bytes", example = "82944")
  Long contentSize;

  @Schema(description = "SHA-256 checksum of uploaded content")
  String checksumSha256;

  @Schema(description = "Document template language")
  DocumentTemplateLanguage language;

  @Schema(
      description = "Optional tenant identifier. Null indicates a global template",
      example = "1")
  Integer tenantId;

  @Schema(
      description =
          "Parsed form map with fields, field types, and possible values for selectable controls")
  DocumentTemplateFormMap formMap;

  @Schema(description = "Parsed e-sign anchor metadata")
  DocumentTemplateEsignAnchorMetadata esignAnchorMetadata;

  @Schema(description = "Whether this template includes configured e-signature anchors")
  boolean esignable;

  @Schema(description = "Timestamp when this record was created")
  Instant createdAt;

  @Schema(description = "Timestamp when this record was last updated")
  Instant updatedAt;

  @Schema(description = "Optimistic-lock version", example = "0")
  Long version;
}
