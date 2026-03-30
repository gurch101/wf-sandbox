package com.gurch.sandbox.esign.internal;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignWebhookRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

class EsignWebhookIntegrationTest extends AbstractEsignIntegrationTest {

  @Test
  void shouldUpdateStatusesFromWebhookAndDownloadCertificate() throws Exception {
    Long id = createRemoteEnvelope();
    String externalEnvelopeId =
        envelopeRepository.findById(id).orElseThrow().getExternalEnvelopeId();

    mockMvc
        .perform(
            signedWebhookRequest(
                new EsignWebhookRequest(
                    externalEnvelopeId,
                    EsignEnvelopeStatus.COMPLETED,
                    Instant.parse("2026-03-26T12:00:00Z"),
                    true,
                    List.of(
                        new EsignWebhookRequest.SignerStatusUpdate(
                            "1",
                            EsignSignerStatus.COMPLETED,
                            Instant.parse("2026-03-26T11:55:00Z"),
                            Instant.parse("2026-03-26T11:59:00Z"))))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.certificateReady").value(true))
        .andExpect(jsonPath("$.signedDocumentReady").value(true))
        .andExpect(jsonPath("$.signers[0].status").value("COMPLETED"));

    mockMvc
        .perform(get("/api/esign/{id}/certificate", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string("Content-Disposition", Matchers.containsString("stub-certificate.pdf")));

    mockMvc
        .perform(get("/api/esign/{id}/signed-document", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header().string("Content-Disposition", Matchers.containsString("remote-esign.pdf")));
  }

  @Test
  void shouldAllowAnonymousWebhookWhenHmacMatches() throws Exception {
    Long id = createRemoteEnvelope();
    String externalEnvelopeId =
        envelopeRepository.findById(id).orElseThrow().getExternalEnvelopeId();

    byte[] payload =
        objectMapper.writeValueAsBytes(
            envelopeEventPayload(
                "envelope-completed", externalEnvelopeId, Instant.parse("2026-03-26T12:00:00Z")));

    mockMvc
        .perform(
            post("/api/esign/webhooks/docusign")
                .with(anonymous())
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .header(DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, hmacSignature(payload)))
        .andExpect(status().isNoContent());
  }

  @Test
  void shouldRejectAnonymousWebhookWhenHmacDoesNotMatch() throws Exception {
    Long id = createRemoteEnvelope();
    String externalEnvelopeId =
        envelopeRepository.findById(id).orElseThrow().getExternalEnvelopeId();

    mockMvc
        .perform(
            post("/api/esign/webhooks/docusign")
                .with(anonymous())
                .header(DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, "bad-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        envelopeEventPayload(
                            "envelope-completed",
                            externalEnvelopeId,
                            Instant.parse("2026-03-26T12:00:00Z")))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void shouldTreatDeclinedEnvelopeAsTerminalFromWebhookRecipientUpdate() throws Exception {
    Long id = createRemoteEnvelope();
    String externalEnvelopeId =
        envelopeRepository.findById(id).orElseThrow().getExternalEnvelopeId();

    mockMvc
        .perform(
            signedWebhookRequest(
                new EsignWebhookRequest(
                    externalEnvelopeId,
                    null,
                    Instant.parse("2026-03-27T12:00:00Z"),
                    false,
                    List.of(
                        new EsignWebhookRequest.SignerStatusUpdate(
                            "1",
                            EsignSignerStatus.DECLINED,
                            Instant.parse("2026-03-27T11:55:00Z"),
                            null)))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"))
        .andExpect(jsonPath("$.signers[0].status").value("DECLINED"));
  }

  private MockHttpServletRequestBuilder signedWebhookRequest(EsignWebhookRequest request)
      throws Exception {
    byte[] payload = objectMapper.writeValueAsBytes(toJsonSimPayload(request));
    return post("/api/esign/webhooks/docusign")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload)
        .header(DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, hmacSignature(payload));
  }

  private DocuSignConnectWebhookPayload toJsonSimPayload(EsignWebhookRequest request) {
    if (request.signers() != null
        && !request.signers().isEmpty()
        && request.signers().get(0).status() != null) {
      EsignWebhookRequest.SignerStatusUpdate signer = request.signers().get(0);
      return recipientEventPayload(
          switch (signer.status()) {
            case SENT -> "recipient-sent";
            case DELIVERED -> "recipient-delivered";
            case COMPLETED -> "recipient-completed";
            case DECLINED -> "recipient-declined";
            case CREATED, VOIDED -> null;
          },
          request.externalEnvelopeId(),
          signer.providerRecipientId(),
          request.eventTimestamp(),
          request.envelopeStatus());
    }
    return envelopeEventPayload(
        switch (request.envelopeStatus()) {
          case CREATED -> "envelope-created";
          case SENT -> "envelope-sent";
          case DELIVERED -> "envelope-delivered";
          case DECLINED -> "envelope-declined";
          case COMPLETED -> "envelope-completed";
          case VOIDED -> "envelope-voided";
          case null -> null;
        },
        request.externalEnvelopeId(),
        request.eventTimestamp());
  }

  private DocuSignConnectWebhookPayload envelopeEventPayload(
      String event, String envelopeId, Instant generatedDateTime) {
    return new DocuSignConnectWebhookPayload(
        event,
        generatedDateTime,
        new DocuSignConnectWebhookPayload.Data(
            envelopeId,
            null,
            new DocuSignConnectWebhookPayload.EnvelopeSummary(
                event == null ? null : event.replace("envelope-", ""))));
  }

  private DocuSignConnectWebhookPayload recipientEventPayload(
      String event,
      String envelopeId,
      String recipientId,
      Instant generatedDateTime,
      EsignEnvelopeStatus envelopeStatus) {
    return new DocuSignConnectWebhookPayload(
        event,
        generatedDateTime,
        new DocuSignConnectWebhookPayload.Data(
            envelopeId,
            recipientId,
            new DocuSignConnectWebhookPayload.EnvelopeSummary(
                envelopeStatus == null ? null : envelopeStatus.name().toLowerCase(Locale.ROOT))));
  }

  private static String hmacSignature(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return Base64.getEncoder().encodeToString(mac.doFinal(payload));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
