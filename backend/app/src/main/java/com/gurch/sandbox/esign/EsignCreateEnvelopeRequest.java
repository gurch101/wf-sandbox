package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/** JSON payload submitted alongside the uploaded PDF when creating an e-sign envelope. */
@Schema(description = "Create-envelope request submitted with the source PDF upload")
public record EsignCreateEnvelopeRequest(
    @Schema(description = "Email subject shown to remote signers", example = "Please sign intake")
        @NotBlank
        String subject,
    @Schema(description = "Optional message shown to signers")
        String message,
    @Schema(description = "Whether the envelope is in-person or remote")
        @NotNull
        EsignDeliveryMode deliveryMode,
    @Schema(description = "Whether automated reminders are enabled for remote signing")
        boolean remindersEnabled,
    @Schema(description = "Reminder interval in hours when reminders are enabled", example = "24")
        @Positive
        Integer reminderIntervalHours,
    @Schema(description = "Ordered signer definitions mapped to PDF anchors")
        @NotEmpty
        List<@Valid SignerInput> signers) {

  /** Nested signer definition. */
  public record SignerInput(
      @Schema(description = "Signature anchor key without surrounding slashes", example = "s1")
          @NotBlank
          String anchorKey,
      @Schema(description = "Signer full name", example = "Pat Doe")
          @NotBlank
          String fullName,
      @Schema(description = "Signer email address", example = "pat@example.com")
          @NotBlank
          @Email
          String email,
      @Schema(description = "Optional phone number for in-person host or SMS delivery")
          String phoneNumber,
      @Schema(description = "Remote signer access method. Use NONE for in-person flows")
          @NotNull
          EsignAuthMethod authMethod,
      @Schema(description = "Access code required when authMethod is PASSCODE")
          String passcode,
      @Schema(description = "SMS number required when authMethod is SMS")
          String smsNumber,
      @Schema(description = "Explicit recipient routing order", example = "1")
          @Positive
          Integer routingOrder) {}
}
