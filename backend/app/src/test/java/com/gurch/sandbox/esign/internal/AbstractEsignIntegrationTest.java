package com.gurch.sandbox.esign.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.esign.EsignApi;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignCreateEnvelopeRequest;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

abstract class AbstractEsignIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;
  @Autowired protected EsignApi esignApi;
  @Autowired protected EsignEnvelopeRepository envelopeRepository;
  @Autowired protected EsignSignerRepository signerRepository;

  @Value("${storage.local-root}")
  protected String storageRoot;

  @BeforeEach
  void setUpEsignIntegration() throws IOException {
    signerRepository.deleteAll();
    envelopeRepository.deleteAll();
    Path root = Path.of(storageRoot);
    if (Files.exists(root)) {
      try (Stream<Path> stream = Files.walk(root)) {
        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
      }
    }
    Files.createDirectories(root);

    Mockito.when(docuSignGateway.createEnvelope(Mockito.any()))
        .thenAnswer(
            invocation -> {
              CreateEnvelopeRequest request = invocation.getArgument(0);
              List<SignerResult> signers =
                  request.getSigners().stream()
                      .map(
                          signer ->
                              new SignerResult(
                                  signer.roleKey(),
                                  String.valueOf(signer.routingOrder()),
                                  EsignSignerStatus.SENT))
                      .toList();
              return new CreateEnvelopeResult(
                  "stub-envelope-id", EsignEnvelopeStatus.SENT, Instant.now(), signers);
            });
    Mockito.doNothing()
        .when(docuSignGateway)
        .voidEnvelope(Mockito.anyString(), Mockito.anyString());
    Mockito.doNothing()
        .when(docuSignGateway)
        .resendRecipients(Mockito.anyString(), Mockito.anyList());
    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList())).thenReturn(List.of());
    Mockito.when(
            docuSignGateway.createRecipientView(
                Mockito.anyString(), Mockito.any(), Mockito.nullable(String.class)))
        .thenAnswer(
            invocation ->
                "https://stub.docusign.local/embed/"
                    + invocation.<String>getArgument(0)
                    + "/"
                    + invocation.<SignerRequest>getArgument(1).roleKey());
    Mockito.when(
            docuSignGateway.downloadCompletedDocument(
                Mockito.anyString(), Mockito.anyString(), Mockito.any(byte[].class)))
        .thenAnswer(
            invocation ->
                new DownloadedDocument(
                    invocation.getArgument(1), "application/pdf", invocation.getArgument(2)));
    Mockito.when(
            docuSignGateway.downloadCertificate(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyList()))
        .thenAnswer(
            invocation ->
                new DownloadedCertificate(
                    "stub-certificate.pdf",
                    "application/pdf",
                    "certificate".getBytes(StandardCharsets.UTF_8)));
  }

  protected Long createRemoteEnvelope() throws Exception {
    return createRemoteEnvelope(null, null);
  }

  protected Long createRemoteEnvelope(String username, String password) throws Exception {
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

    MvcResult result =
        mockMvc
            .perform(
                username == null
                    ? multipart("/api/esign").file(file).file(request)
                    : multipart("/api/esign")
                        .file(file)
                        .file(request)
                        .with(httpBasic(username, password)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
  }

  protected Long createInPersonEnvelope() throws Exception {
    byte[] payload = loadIntrospectPdf();
    MockMultipartFile file =
        new MockMultipartFile("file", "in-person-esign.pdf", "application/pdf", payload);
    MockMultipartFile request =
        new MockMultipartFile(
            "request",
            "",
            MediaType.APPLICATION_JSON_VALUE,
            objectMapper.writeValueAsBytes(
                new EsignCreateEnvelopeRequest(
                    "Please sign in person",
                    null,
                    EsignDeliveryMode.IN_PERSON,
                    false,
                    null,
                    List.of(
                        new EsignCreateEnvelopeRequest.SignerInput(
                            "s1", "Pat Doe", null, EsignAuthMethod.NONE, null, null, 1)))));

    MvcResult result =
        mockMvc
            .perform(multipart("/api/esign").file(file).file(request))
            .andExpect(status().isCreated())
            .andReturn();
    Long id = objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    mockMvc
        .perform(
            post("/api/esign/{id}/signers/{roleKey}/embedded-view", id, "s1")
                .header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roleKey").value("s1"))
        .andExpect(
            jsonPath("$.signingUrl")
                .value(Matchers.startsWith("https://stub.docusign.local/embed/")));
    return id;
  }

  protected byte[] loadIntrospectPdf() throws IOException {
    try (InputStream inputStream =
        getClass().getResourceAsStream("/documenttemplates/introspect.pdf")) {
      assertThat(inputStream).isNotNull();
      return inputStream.readAllBytes();
    }
  }
}
