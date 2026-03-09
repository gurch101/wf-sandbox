package com.gurch.sandbox.esign;

import com.gurch.sandbox.idempotency.NotIdempotent;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/docusign")
public class EsignWebhookController {

  private final EsignApi esignApi;

  public EsignWebhookController(EsignApi esignApi) {
    this.esignApi = esignApi;
  }

  @PostMapping("/status")
  @NotIdempotent
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void handleStatusUpdate(
      @RequestBody @Valid WebhookStatusRequest request,
      @RequestHeader(value = "X-DocuSign-Signature-1", required = false) String signatureHeader) {
    esignApi.handleWebhookStatusUpdate(
        EsignWebhookStatusUpdate.builder()
            .eventId(request.getEventId())
            .envelopeId(request.getEnvelopeId())
            .status(request.getStatus())
            .statusChangedAt(request.getStatusChangedAt())
            .build(),
        signatureHeader);
  }

  @Value
  @Schema(description = "DocuSign webhook envelope status payload")
  public static class WebhookStatusRequest {
    @NotBlank(message = "eventId is required")
    String eventId;

    @NotBlank(message = "envelopeId is required")
    String envelopeId;

    @NotBlank(message = "status is required")
    String status;

    Instant statusChangedAt;
  }
}
