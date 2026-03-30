package com.gurch.sandbox.esign.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request for voiding an active envelope.
 *
 * @param reason reason recorded against the voided envelope
 */
@Schema(description = "Void-envelope request")
public record EsignVoidRequest(
    @Schema(description = "Reason recorded against the voided envelope") @NotBlank String reason) {}
