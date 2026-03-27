package com.gurch.sandbox.esign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.esign.internal.DocuSignGateway;
import com.gurch.sandbox.esign.internal.DocuSignWebhookVerifier;
import com.gurch.sandbox.esign.internal.EsignEnvelopeRepository;
import com.gurch.sandbox.esign.internal.EsignSignerRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@WithMockUser(username = "1")
class EsignModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private EsignEnvelopeRepository envelopeRepository;
  @Autowired private EsignSignerRepository signerRepository;
  @MockitoBean private DocuSignGateway docuSignGateway;

  @Value("${storage.local-root}")
  private String storageRoot;

  @BeforeEach
  void setUp() throws IOException {
    signerRepository.deleteAll();
    envelopeRepository.deleteAll();
    Path root = Path.of(storageRoot);
    if (Files.exists(root)) {
      try (Stream<Path> stream = Files.walk(root)) {
        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
      }
    }
    Files.createDirectories(root);

    Mockito
        .when(docuSignGateway.createEnvelope(Mockito.any()))
        .thenAnswer(
            invocation -> {
              DocuSignGateway.CreateEnvelopeRequest request = invocation.getArgument(0);
              List<DocuSignGateway.SignerResult> signers =
                  request.getSigners().stream()
                      .map(
                          signer ->
                              new DocuSignGateway.SignerResult(
                                  signer.roleKey(),
                                  String.valueOf(signer.routingOrder()),
                                  EsignSignerStatus.SENT))
                      .toList();
              return new DocuSignGateway.CreateEnvelopeResult(
                  "stub-envelope-id", EsignEnvelopeStatus.SENT, java.time.Instant.now(), signers);
            });
    Mockito.doNothing().when(docuSignGateway).voidEnvelope(Mockito.anyString(), Mockito.anyString());
    Mockito.doNothing().when(docuSignGateway).deleteEnvelope(Mockito.anyString());
    Mockito
        .when(docuSignGateway.createRecipientView(Mockito.anyString(), Mockito.any()))
        .thenAnswer(
            invocation ->
                "https://stub.docusign.local/embed/"
                    + invocation.<String>getArgument(0)
                    + "/"
                    + invocation.<DocuSignGateway.SignerRequest>getArgument(1).roleKey());
    Mockito
        .when(
            docuSignGateway.downloadCompletedDocument(
                Mockito.anyString(), Mockito.anyString(), Mockito.any(byte[].class)))
        .thenAnswer(
            invocation ->
                new DocuSignGateway.DownloadedDocument(
                    invocation.getArgument(1), "application/pdf", invocation.getArgument(2)));
    Mockito
        .when(
            docuSignGateway.downloadCertificate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyList()))
        .thenReturn(
            new DocuSignGateway.DownloadedCertificate(
                "stub-certificate.pdf",
                "application/pdf",
                "certificate".getBytes(StandardCharsets.UTF_8)));
  }

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
            objectMapper
                .writeValueAsBytes(
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
                                "555-1111",
                                EsignAuthMethod.PASSCODE,
                                "4321",
                                null,
                                1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign/envelopes").file(file).file(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.externalEnvelopeId").value(org.hamcrest.Matchers.startsWith("stub-")))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.deliveryMode").value("REMOTE"))
            .andExpect(jsonPath("$.certificateReady").value(false))
            .andExpect(jsonPath("$.signedDocumentReady").value(false))
            .andExpect(jsonPath("$.signers.length()").value(1))
            .andExpect(jsonPath("$.signers[0].signatureAnchorText").value("/s1/"))
            .andExpect(jsonPath("$.signers[0].dateAnchorText").isEmpty())
            .andExpect(jsonPath("$.signers[0].authMethod").value("PASSCODE"))
            .andReturn();

    Long id =
        objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();

    mockMvc
        .perform(get("/api/esign/envelopes/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.remindersEnabled").value(true))
        .andExpect(jsonPath("$.reminderIntervalHours").value(24));
  }

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
                    java.time.Instant.parse("2026-03-26T12:00:00Z"),
                    true,
                    List.of(
                        new EsignWebhookRequest.SignerStatusUpdate(
                            "s1",
                            "recipient-1",
                            EsignSignerStatus.COMPLETED,
                            java.time.Instant.parse("2026-03-26T11:55:00Z"),
                            java.time.Instant.parse("2026-03-26T11:59:00Z"))))))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/esign/envelopes/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.certificateReady").value(true))
        .andExpect(jsonPath("$.signedDocumentReady").value(true))
        .andExpect(jsonPath("$.signers[0].status").value("COMPLETED"));

    mockMvc
        .perform(get("/api/esign/envelopes/{id}/certificate", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header().string(
                "Content-Disposition", org.hamcrest.Matchers.containsString("stub-certificate.pdf")));

    mockMvc
        .perform(get("/api/esign/envelopes/{id}/signed-document", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header().string(
                "Content-Disposition",
                org.hamcrest.Matchers.containsString("remote-esign.pdf")));
  }

  @Test
  void shouldAllowAnonymousWebhookWhenHmacMatches() throws Exception {
    Long id = createRemoteEnvelope();
    String externalEnvelopeId =
        envelopeRepository.findById(id).orElseThrow().getExternalEnvelopeId();

    byte[] payload =
        objectMapper.writeValueAsBytes(
            new EsignWebhookRequest(
                externalEnvelopeId,
                EsignEnvelopeStatus.COMPLETED,
                java.time.Instant.parse("2026-03-26T12:00:00Z"),
                true,
                List.of()));

    mockMvc
        .perform(
            post("/api/esign/envelopes/webhooks/docusign")
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
            post("/api/esign/envelopes/webhooks/docusign")
                .with(anonymous())
                .header(DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, "bad-signature")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsBytes(
                        new EsignWebhookRequest(
                            externalEnvelopeId,
                            EsignEnvelopeStatus.COMPLETED,
                            java.time.Instant.parse("2026-03-26T12:00:00Z"),
                            true,
                            List.of()))))
        .andExpect(status().isUnauthorized());
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signedWebhookRequest(
      EsignWebhookRequest request) throws Exception {
    byte[] payload = objectMapper.writeValueAsBytes(request);
    return post("/api/esign/envelopes/webhooks/docusign")
        .contentType(MediaType.APPLICATION_JSON)
        .content(payload)
        .header(DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, hmacSignature(payload));
  }

  private static String hmacSignature(byte[] payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      return java.util.Base64.getEncoder().encodeToString(mac.doFinal(payload));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  @Test
  void shouldVoidAndDeleteEnvelope() throws Exception {
    Long id = createInPersonEnvelope();

    mockMvc
        .perform(
            post("/api/esign/envelopes/{id}/void", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(new EsignVoidRequest("Signer requested stop"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOIDED"))
        .andExpect(jsonPath("$.voidedReason").value("Signer requested stop"))
        .andExpect(jsonPath("$.signers[0].status").value("VOIDED"));

    mockMvc
        .perform(
            delete("/api/esign/envelopes/{id}", id)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/esign/envelopes/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("E-sign envelope not found with id: " + id));
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
            objectMapper
                .writeValueAsBytes(
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
                                null,
                                EsignAuthMethod.PASSCODE,
                                "1111",
                                null,
                                1)))));

    mockMvc
        .perform(multipart("/api/esign/envelopes").file(file).file(request))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].code").value("MISSING_SIGNATURE_ANCHOR"));
  }

  private Long createRemoteEnvelope() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "remote-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper
                .writeValueAsBytes(
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
                                "555-1111",
                                EsignAuthMethod.PASSCODE,
                                "4321",
                                null,
                                1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign/envelopes").file(file).file(request))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  private Long createInPersonEnvelope() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "in-person-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper
                .writeValueAsBytes(
                    new EsignCreateEnvelopeRequest(
                        "Please sign in person",
                        null,
                        EsignDeliveryMode.IN_PERSON,
                        false,
                        null,
                        List.of(
                            new EsignCreateEnvelopeRequest.SignerInput(
                                "s1",
                                "Pat Doe",
                                "pat@example.com",
                                "555-1111",
                                EsignAuthMethod.NONE,
                                null,
                                null,
                                1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign/envelopes").file(file).file(request))
            .andExpect(status().isCreated())
            .andReturn();
    Long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    mockMvc
        .perform(
            post("/api/esign/envelopes/{id}/signers/{roleKey}/embedded-view", id, "s1")
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleKey").value("s1"))
        .andExpect(jsonPath("$.signingUrl").value(org.hamcrest.Matchers.startsWith("https://stub.docusign.local/embed/")));
    return id;
  }

  private byte[] loadIntrospectPdf() throws IOException {
    try (InputStream inputStream =
        getClass().getResourceAsStream("/documenttemplates/introspect.pdf")) {
      assertThat(inputStream).isNotNull();
      return inputStream.readAllBytes();
    }
  }
}
