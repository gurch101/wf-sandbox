package com.gurch.sandbox.esign.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignCreateEnvelopeRequest;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerDeliveryMethod;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignVoidRequest;
import com.gurch.sandbox.web.NotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

class EsignServiceIntegrationTest extends AbstractEsignIntegrationTest {

  @Test
  void shouldCreateRemoteEnvelopeFromPdfAnchorsAndQueryStatus() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Review and sign",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1",
                            "Pat Doe",
                            "pat@example.com",
                            EsignAuthMethod.PASSCODE,
                            "4321",
                            null,
                            1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign").file(file).file(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.externalEnvelopeId").value(Matchers.startsWith("stub-")))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.deliveryMode").value("REMOTE"))
            .andExpect(jsonPath("$.certificateReady").value(false))
            .andExpect(jsonPath("$.signedDocumentReady").value(false))
            .andExpect(jsonPath("$.signers.length()").value(1))
            .andExpect(jsonPath("$.signers[0].signatureAnchorText").value("/s1/"))
            .andExpect(jsonPath("$.signers[0].dateAnchorText").isEmpty())
            .andExpect(jsonPath("$.signers[0].authMethod").value("PASSCODE"))
            .andReturn();

    Long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.remindersEnabled").value(true))
        .andExpect(jsonPath("$.reminderIntervalHours").value(24));
  }

  @Test
  void shouldRequireSignerEmailForRemoteEnvelope() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Review and sign",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1", "Pat Doe", null, EsignAuthMethod.PASSCODE, "4321", null, 1)))));

    mockMvc
        .perform(multipart("/api/esign").file(file).file(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("REMOTE_EMAIL_REQUIRED"));
  }

  @Test
  void shouldNormalizeSmsNumberToE164ForRemoteSmsSigner() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Review and sign",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1",
                            "Pat Doe",
                            null,
                            EsignAuthMethod.NONE,
                            null,
                            "(415) 555-2671",
                            EsignSignerDeliveryMethod.SMS,
                            1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign").file(file).file(request))
            .andExpect(status().isCreated())
            .andReturn();

    Long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    var signer = signerRepository.findByEnvelopeId(id).get(0);
    assertThat(signer.getSmsNumber()).isEqualTo("+14155552671");
    assertThat(signer.getEmail()).isNull();
    assertThat(signer.getDeliveryMethod()).isEqualTo(EsignSignerDeliveryMethod.SMS);
  }

  @Test
  void shouldRejectInvalidSmsNumberForRemoteSmsSigner() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Review and sign",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1",
                            "Pat Doe",
                            null,
                            EsignAuthMethod.NONE,
                            null,
                            "abc",
                            EsignSignerDeliveryMethod.SMS,
                            1)))));

    mockMvc
        .perform(multipart("/api/esign").file(file).file(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("SMS_NUMBER_INVALID"));
  }

  @Test
  void shouldRejectSmsDeliveryCombinedWithSmsAuthentication() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Review and sign",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1",
                            "Pat Doe",
                            null,
                            EsignAuthMethod.SMS,
                            null,
                            "+14155552671",
                            EsignSignerDeliveryMethod.SMS,
                            1)))));

    mockMvc
        .perform(multipart("/api/esign").file(file).file(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("SMS_DELIVERY_SMS_AUTH_NOT_SUPPORTED"));
  }

  @Test
  void shouldRestrictNullTenantEnvelopeToNullTenantUsers() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(existing.toBuilder().tenantId(null).build());
    assertThat(envelopeRepository.findById(id).orElseThrow().getTenantId()).isNull();

    Mockito.when(currentUserProvider.currentUserId()).thenReturn(Optional.of(2));
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(1));
    assertThatThrownBy(() -> esignApi.findById(id)).isInstanceOf(NotFoundException.class);

    Mockito.when(currentUserProvider.currentUserId()).thenReturn(Optional.of(3));
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
    var envelope = esignApi.findById(id);
    assertThat(envelope).isPresent();
    assertThat(envelope.orElseThrow().getTenantId()).isNull();
  }

  @Test
  void shouldVoidEnvelopeAndKeepItQueryable() throws Exception {
    Long id = createInPersonEnvelope();

    mockMvc
        .perform(
            post("/api/esign/{id}/void", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(new EsignVoidRequest("Signer requested stop"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOIDED"))
        .andExpect(jsonPath("$.voidedReason").value("Signer requested stop"))
        .andExpect(jsonPath("$.signers[0].status").value("VOIDED"));

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOIDED"))
        .andExpect(jsonPath("$.voidedReason").value("Signer requested stop"));
  }

  @Test
  void shouldResendEnvelopeForActionableRemoteSigners() throws Exception {
    Long id = createRemoteEnvelope();
    var signer = signerRepository.findByEnvelopeId(id).get(0);
    signerRepository.save(signer.toBuilder().status(EsignSignerStatus.DELIVERED).build());

    mockMvc
        .perform(
            post("/api/esign/{id}/resend", id)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.signers[0].status").value("DELIVERED"));

    Mockito.verify(docuSignGateway)
        .resendRecipients(
            Mockito.eq("stub-envelope-id"),
            Mockito.argThat(
                signers ->
                    signers.size() == 1
                        && "s1".equals(signers.get(0).roleKey())
                        && "1".equals(signers.get(0).providerRecipientId())));
  }

  @Test
  void shouldResendSpecificRemoteSigner() throws Exception {
    Long id = createRemoteEnvelope();

    mockMvc
        .perform(
            post("/api/esign/{id}/signers/{roleKey}/resend", id, "s1")
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id));

    Mockito.verify(docuSignGateway)
        .resendRecipients(
            Mockito.eq("stub-envelope-id"),
            Mockito.argThat(
                signers ->
                    signers.size() == 1
                        && "s1".equals(signers.get(0).roleKey())
                        && "1".equals(signers.get(0).providerRecipientId())));
  }

  @Test
  void shouldRejectResendForInPersonEnvelope() throws Exception {
    Long id = createInPersonEnvelope();

    mockMvc
        .perform(
            post("/api/esign/{id}/resend", id)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("RESEND_REMOTE_ONLY"));
  }

  @Test
  void shouldRejectResendWhenEnvelopeHasNoActionableSigners() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(existing.toBuilder().status(EsignEnvelopeStatus.COMPLETED).build());

    mockMvc
        .perform(
            post("/api/esign/{id}/resend", id)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("ENVELOPE_NOT_ACTIONABLE"));
  }

  @Test
  void shouldRejectResendForNonActionableSigner() throws Exception {
    Long id = createRemoteEnvelope();
    var signer = signerRepository.findByEnvelopeId(id).get(0);
    signerRepository.save(signer.toBuilder().status(EsignSignerStatus.COMPLETED).build());

    mockMvc
        .perform(
            post("/api/esign/{id}/signers/{roleKey}/resend", id, "s1")
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.errors[0].code").value("SIGNER_NOT_ACTIONABLE"));
  }

  @Test
  void shouldRejectCertificateDownloadWhenCompletedEnvelopeHasNotStoredCertificate()
      throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(
        existing.toBuilder()
            .status(EsignEnvelopeStatus.COMPLETED)
            .completedAt(Instant.parse("2026-03-27T10:00:00Z"))
            .certificateStorageObjectId(null)
            .build());

    mockMvc
        .perform(get("/api/esign/{id}/certificate", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].name").value("id"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Requested file is not available until the envelope is completed"));

    Mockito.verify(docuSignGateway, Mockito.never())
        .downloadCertificate(Mockito.anyString(), Mockito.anyString(), Mockito.anyList());
  }

  @Test
  void shouldRejectSignedDocumentDownloadWhenCompletedEnvelopeHasNotStoredSignedDocument()
      throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(
        existing.toBuilder()
            .status(EsignEnvelopeStatus.COMPLETED)
            .completedAt(Instant.parse("2026-03-27T10:00:00Z"))
            .signedStorageObjectId(null)
            .build());

    mockMvc
        .perform(get("/api/esign/{id}/signed-document", id))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].name").value("id"))
        .andExpect(
            jsonPath("$.errors[0].message")
                .value("Requested file is not available until the envelope is completed"));

    Mockito.verify(docuSignGateway, Mockito.never())
        .downloadCompletedDocument(
            Mockito.anyString(), Mockito.anyString(), Mockito.any(byte[].class));
  }

  @Test
  void shouldRejectRemoteEnvelopeWhenRequiredAnchorMissing() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "missing-anchor.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    null,
                    EsignDeliveryMode.REMOTE,
                    false,
                    null,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s2",
                            "Pat Doe",
                            "pat@example.com",
                            EsignAuthMethod.PASSCODE,
                            "1111",
                            null,
                            1)))));

    mockMvc
        .perform(multipart("/api/esign").file(file).file(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("MISSING_SIGNATURE_ANCHOR"));
  }

  @Test
  void shouldCleanupPendingEnvelopeWhenDocuSignCreateFails() throws Exception {
    Mockito.doThrow(new IllegalStateException("DocuSign unavailable"))
        .when(docuSignGateway)
        .createEnvelope(Mockito.any());

    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign",
                    "Remote flow",
                    EsignDeliveryMode.REMOTE,
                    true,
                    24,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1",
                            "Pat Doe",
                            "pat@example.com",
                            EsignAuthMethod.PASSCODE,
                            "4321",
                            null,
                            1)))));

    assertThatThrownBy(() -> mockMvc.perform(multipart("/api/esign").file(file).file(request)))
        .isInstanceOf(Exception.class)
        .hasRootCauseInstanceOf(IllegalStateException.class);

    assertThat(envelopeRepository.count()).isZero();
    assertThat(signerRepository.count()).isZero();
    try (Stream<Path> stream = Files.walk(Path.of(storageRoot))) {
      assertThat(stream.filter(Files::isRegularFile)).isEmpty();
    }
  }

  @Test
  void shouldAttemptToVoidProviderEnvelopeWhenLocalFinalizationFails() throws Exception {
    createRemoteEnvelope();

    Throwable thrown = catchThrowable(() -> createRemoteEnvelope());

    assertThat(thrown).isNotNull();
    assertThat(thrown).hasCauseInstanceOf(IllegalStateException.class);
    assertThat(thrown.getCause())
        .hasMessageContaining("externalEnvelopeId: stub-envelope-id")
        .hasMessageContaining("providerCleanupSucceeded: true");
    Mockito.verify(docuSignGateway)
        .voidEnvelope(
            Mockito.eq("stub-envelope-id"),
            Mockito.contains("Local finalization failed after provider envelope creation"));
  }

  @Test
  void shouldReportFailedProviderCleanupWhenVoidAlsoFails() throws Exception {
    createRemoteEnvelope();
    Mockito.doThrow(new IllegalStateException("DocuSign void unavailable"))
        .when(docuSignGateway)
        .voidEnvelope(Mockito.eq("stub-envelope-id"), Mockito.anyString());

    Throwable thrown = catchThrowable(() -> createRemoteEnvelope());

    assertThat(thrown).isNotNull();
    assertThat(thrown).hasCauseInstanceOf(IllegalStateException.class);
    assertThat(thrown.getCause())
        .hasMessageContaining("externalEnvelopeId: stub-envelope-id")
        .hasMessageContaining("providerCleanupSucceeded: false");
    Mockito.verify(docuSignGateway)
        .voidEnvelope(
            Mockito.eq("stub-envelope-id"),
            Mockito.contains("Local finalization failed after provider envelope creation"));
  }
}
