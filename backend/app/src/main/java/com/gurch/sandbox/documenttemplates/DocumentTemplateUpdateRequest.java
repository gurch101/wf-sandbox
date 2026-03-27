package com.gurch.sandbox.documenttemplates;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Multipart metadata payload for document template updates.
 *
 * @param enName optional English template name
 * @param frName optional French template name
 * @param enDescription optional English template description
 * @param frDescription optional French template description
 */
@Schema(description = "Multipart request metadata for updating a document template")
public record DocumentTemplateUpdateRequest(
    @Schema(description = "Optional English template name", example = "Updated Form Name")
        String enName,
    @Schema(description = "Optional French template name", example = "Nom mis a jour")
        String frName,
    @Schema(description = "Optional English description", example = "Updated description")
        String enDescription,
    @Schema(description = "Optional French description", example = "Description mise a jour")
        String frDescription) {}
