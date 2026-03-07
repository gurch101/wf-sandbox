package com.gurch.sandbox.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Details about a validation failure for a specific field.
 *
 * @param name the field name
 * @param code the error code
 * @param message the human-readable reason
 */
@Schema(description = "Validation error details")
public record ValidationError(
    @Schema(description = "Name of the invalid field", example = "name") String name,
    @Schema(description = "Validation error code", example = "NotBlank") String code,
    @Schema(description = "Reason for the validation failure", example = "must not be blank")
        String message) {}
