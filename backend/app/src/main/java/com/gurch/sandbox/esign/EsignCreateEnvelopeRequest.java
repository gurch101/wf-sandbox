package com.gurch.sandbox.esign;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * JSON payload submitted alongside the uploaded PDF when creating an e-sign envelope.
 *
 * @param subject email subject shown to remote signers
 * @param message optional message shown to signers
 * @param deliveryMode whether the envelope is in-person or remote
 * @param remindersEnabled whether automated reminders are enabled for remote signing
 * @param reminderIntervalHours reminder interval in hours when reminders are enabled
 * @param signers ordered signer definitions mapped to PDF anchors
 */
@Schema(description = "Create-envelope request submitted with the source PDF upload")
public record EsignCreateEnvelopeRequest(
    @Schema(description = "Email subject shown to remote signers", example = "Please sign intake")
        @NotBlank
        String subject,
    @Schema(description = "Optional message shown to signers") String message,
    @Schema(description = "Whether the envelope is in-person or remote") @NotNull
        EsignDeliveryMode deliveryMode,
    @Schema(description = "Whether automated reminders are enabled for remote signing")
        boolean remindersEnabled,
    @Schema(description = "Reminder interval in hours when reminders are enabled", example = "24")
        @Positive
        Integer reminderIntervalHours,
    @Schema(description = "Ordered signer definitions mapped to PDF anchors") @NotEmpty
        List<@Valid SignerInput> signers) {

  /**
   * Creates a request with an immutable copy of signer definitions.
   *
   * @param subject email subject shown to remote signers
   * @param message optional message shown to signers
   * @param deliveryMode whether the envelope is in-person or remote
   * @param remindersEnabled whether automated reminders are enabled for remote signing
   * @param reminderIntervalHours reminder interval in hours when reminders are enabled
   * @param signers ordered signer definitions mapped to PDF anchors
   */
  public EsignCreateEnvelopeRequest {
    signers = signers == null ? List.of() : List.copyOf(signers);
  }

  /**
   * Nested signer definition.
   *
   * @param anchorKey signature anchor key without surrounding slashes
   * @param fullName signer full name
   * @param email signer email address; required for remote email delivery and optional otherwise
   * @param authMethod signer access method
   * @param passcode access code required when authMethod is PASSCODE
   * @param smsNumber SMS number required for SMS delivery and SMS authentication
   * @param deliveryMethod delivery channel for remote signers; defaults to EMAIL
   * @param routingOrder explicit recipient routing order
   */
  public record SignerInput(
      @Schema(description = "Signature anchor key without surrounding slashes", example = "s1")
          @NotBlank
          String anchorKey,
      @Schema(description = "Signer full name", example = "Pat Doe") @NotBlank String fullName,
      @Schema(
              description =
                  "Signer email address. Required for remote EMAIL delivery, "
                      + "optional for remote SMS delivery and in-person signing",
              example = "pat@example.com")
          String email,
      @Schema(description = "Remote signer access method. Use NONE for in-person flows") @NotNull
          EsignAuthMethod authMethod,
      @Schema(description = "Access code required when authMethod is PASSCODE") String passcode,
      @Schema(
              description =
                  "SMS number required for SMS delivery and SMS authentication. "
                      + "Common US formats are accepted and normalized to E.164",
              example = "+14155551234")
          String smsNumber,
      @Schema(
              description = "Delivery channel for remote signers. Defaults to EMAIL when omitted",
              example = "EMAIL")
          EsignSignerDeliveryMethod deliveryMethod,
      @Schema(description = "Explicit recipient routing order", example = "1") @Positive
          Integer routingOrder) {

    /**
     * Compatibility constructor that defaults remote signer delivery to EMAIL.
     *
     * @param anchorKey signature anchor key without surrounding slashes
     * @param fullName signer full name
     * @param email signer email address
     * @param authMethod signer access method
     * @param passcode access code required when authMethod is PASSCODE
     * @param smsNumber SMS number required for SMS delivery and SMS authentication
     * @param routingOrder explicit recipient routing order
     */
    public SignerInput(
        String anchorKey,
        String fullName,
        String email,
        EsignAuthMethod authMethod,
        String passcode,
        String smsNumber,
        Integer routingOrder) {
      this(anchorKey, fullName, email, authMethod, passcode, smsNumber, null, routingOrder);
    }
  }
}
