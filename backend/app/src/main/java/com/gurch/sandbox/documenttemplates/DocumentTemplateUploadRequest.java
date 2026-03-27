package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Multipart metadata payload for document template uploads.
 *
 * @param enName English template name
 * @param frName optional French template name
 * @param enDescription optional English template description
 * @param frDescription optional French template description
 * @param language template language
 * @param tenantId optional tenant scope
 */
@Schema(description = "Multipart request metadata for uploading a document template")
public record DocumentTemplateUploadRequest(
    @Schema(description = "English template name", example = "Client Intake Form.pdf")
        @NotBlank(message = "enName is required")
        String enName,
    @Schema(description = "Optional French template name", example = "Formulaire d'accueil client")
        String frName,
    @Schema(description = "Optional English description", example = "Onboarding package")
        String enDescription,
    @Schema(description = "Optional French description", example = "Dossier d'integration")
        String frDescription,
    @Schema(description = "Template language", example = "ENGLISH")
        @NotNull(message = "language is required")
        DocumentTemplateLanguage language,
    @Schema(description = "Optional tenant scope") Integer tenantId) {}
