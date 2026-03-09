package com.gurch.sandbox.esign;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.esign.internal.DocusignEnvelopeArtifacts;
import com.gurch.sandbox.esign.internal.DocusignEnvelopeRequest;
import com.gurch.sandbox.esign.internal.DocusignEnvelopeResult;
import java.io.ByteArrayOutputStream;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class EsignModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private EsignApi esignApi;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void shouldStartRemoteEnvelopeFromPdfContract() throws Exception {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    when(docusignEsignGateway.createEnvelope(any(), any()))
        .thenReturn(new DocusignEnvelopeResult("env-1", null));

    StartEsignResponse response =
        esignApi.startRemote(
            StartRemoteEsignRequest.builder()
                .requestId(42L)
                .documentName("generated-document-bundle.pdf")
                .documentPdf(createPdfWithAnchors("s1 d1 s2 d2"))
                .idempotencyKey("idem-42")
                .signer(
                    StartRemoteEsignRequest.SignerRecipient.builder()
                        .recipientId("s1")
                        .name("Signer One")
                        .email("signer1@example.com")
                        .routingOrder(1)
                        .build())
                .signer(
                    StartRemoteEsignRequest.SignerRecipient.builder()
                        .recipientId("s2")
                        .name("Signer Two")
                        .email("signer2@example.com")
                        .routingOrder(2)
                        .build())
                .ccRecipient(
                    StartRemoteEsignRequest.CcRecipient.builder()
                        .recipientId("c1")
                        .name("Watcher")
                        .email("watcher@example.com")
                        .routingOrder(3)
                        .build())
                .build());

    assertThat(response.getRequestId()).isEqualTo(42L);
    assertThat(response.getEnvelopeId()).isEqualTo("env-1");
    assertThat(response.getSignatureMode()).isEqualTo(EsignSignatureMode.REMOTE);
    assertThat(response.getStatus()).isEqualTo(EsignEnvelopeStatus.CREATED);
    assertThat(response.getRecipientViewUrl()).isNull();
    assertThat(response.getCreatedAt()).isNotNull();

    ArgumentCaptor<DocusignEnvelopeRequest> captor =
        ArgumentCaptor.forClass(DocusignEnvelopeRequest.class);
    verify(docusignEsignGateway).createEnvelope(any(), captor.capture());
    DocusignEnvelopeRequest envelopeRequest = captor.getValue();
    assertThat(envelopeRequest.getIdempotencyKey()).isEqualTo("idem-42");
    assertThat(envelopeRequest.getSigners()).hasSize(2);
    assertThat(envelopeRequest.getSigners().get(0).getSignAnchor()).isEqualTo("s1");
    assertThat(envelopeRequest.getSigners().get(0).getDateAnchor()).isEqualTo("d1");
    assertThat(envelopeRequest.getSigners().get(0).getRemoteDeliveryMethod())
        .isEqualTo(EsignRemoteDeliveryMethod.EMAIL);
    assertThat(envelopeRequest.getSigners().get(1).getSignAnchor()).isEqualTo("s2");
    assertThat(envelopeRequest.getSigners().get(1).getDateAnchor()).isEqualTo("d2");
  }

  @Test
  void shouldStartEmbeddedEnvelopeAndReturnRecipientViewUrl() throws Exception {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    when(docusignEsignGateway.createEnvelope(any(), any()))
        .thenReturn(new DocusignEnvelopeResult("env-2", "https://embedded.example/view"));

    StartEsignResponse response =
        esignApi.startEmbedded(
            StartEmbeddedEsignRequest.builder()
                .requestId(43L)
                .documentName("generated-document-bundle.pdf")
                .documentPdf(createPdfWithAnchors("s1 d1"))
                .signer(
                    StartEmbeddedEsignRequest.SignerRecipient.builder()
                        .recipientId("s1")
                        .name("In Person One")
                        .email("inperson@example.com")
                        .routingOrder(1)
                        .build())
                .build());

    assertThat(response.getEnvelopeId()).isEqualTo("env-2");
    assertThat(response.getRecipientViewUrl()).isEqualTo("https://embedded.example/view");
  }

  @Test
  void shouldRejectPdfMissingRequiredSignatureAnchor() throws Exception {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));

    StartRemoteEsignRequest request =
        StartRemoteEsignRequest.builder()
            .requestId(44L)
            .documentName("generated-document-bundle.pdf")
            .documentPdf(createPdfWithAnchors("d1 only"))
            .signer(
                StartRemoteEsignRequest.SignerRecipient.builder()
                    .recipientId("s1")
                    .name("Signer One")
                    .email("signer1@example.com")
                    .routingOrder(1)
                    .build())
            .build();

    assertThatThrownBy(() -> esignApi.startRemote(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PDF is missing required signature anchor: s1");
  }

  @Test
  void shouldSupportRemoteSmsDeliveryAndAccessCode() throws Exception {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    when(docusignEsignGateway.createEnvelope(any(), any()))
        .thenReturn(new DocusignEnvelopeResult("env-sms", null));

    esignApi.startRemote(
        StartRemoteEsignRequest.builder()
            .requestId(45L)
            .documentName("generated-document-bundle.pdf")
            .documentPdf(createPdfWithAnchors("s1 d1"))
            .idempotencyKey("idem-45")
            .signer(
                StartRemoteEsignRequest.SignerRecipient.builder()
                    .recipientId("s1")
                    .name("Signer SMS")
                    .routingOrder(1)
                    .remoteDeliveryMethod(EsignRemoteDeliveryMethod.SMS)
                    .smsCountryCode("1")
                    .smsNumber("4155551212")
                    .accessCode("1234")
                    .build())
            .build());

    ArgumentCaptor<DocusignEnvelopeRequest> captor =
        ArgumentCaptor.forClass(DocusignEnvelopeRequest.class);
    verify(docusignEsignGateway).createEnvelope(any(), captor.capture());
    DocusignEnvelopeRequest.SignerRecipient signer = captor.getValue().getSigners().getFirst();
    assertThat(signer.getRemoteDeliveryMethod()).isEqualTo(EsignRemoteDeliveryMethod.SMS);
    assertThat(signer.getSmsCountryCode()).isEqualTo("1");
    assertThat(signer.getSmsNumber()).isEqualTo("4155551212");
    assertThat(signer.getAccessCode()).isEqualTo("1234");
  }

  @Test
  void shouldRejectRemoteSmsSignerWhenPhoneMissing() throws Exception {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));

    StartRemoteEsignRequest request =
        StartRemoteEsignRequest.builder()
            .requestId(46L)
            .documentName("generated-document-bundle.pdf")
            .documentPdf(createPdfWithAnchors("s1 d1"))
            .signer(
                StartRemoteEsignRequest.SignerRecipient.builder()
                    .recipientId("s1")
                    .name("Signer SMS")
                    .routingOrder(1)
                    .remoteDeliveryMethod(EsignRemoteDeliveryMethod.SMS)
                    .build())
            .build();

    assertThatThrownBy(() -> esignApi.startRemote(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "signer.smsCountryCode and signer.smsNumber are required for remote SMS delivery");
  }

  @Test
  void shouldVoidEnvelope() {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    doNothing().when(docusignEsignGateway).voidEnvelope(any(), eq("env-void"), eq("cancel"));

    esignApi.voidEnvelope(
        VoidEsignEnvelopeRequest.builder().envelopeId("env-void").reason("cancel").build());

    verify(docusignEsignGateway).voidEnvelope(any(), eq("env-void"), eq("cancel"));
  }

  @Test
  void shouldResendEnvelope() {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    doNothing().when(docusignEsignGateway).resendEnvelope(any(), eq("env-resend"));

    esignApi.resendEnvelope(ResendEsignEnvelopeRequest.builder().envelopeId("env-resend").build());

    verify(docusignEsignGateway).resendEnvelope(any(), eq("env-resend"));
  }

  @Test
  void shouldDownloadSignedArtifacts() {
    Integer tenantId = createTenantWithDocusignConfig();
    when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    when(docusignEsignGateway.downloadArtifacts(any(), eq("env-download")))
        .thenReturn(
            DocusignEnvelopeArtifacts.builder()
                .signedDocumentPdf(new byte[] {1, 2})
                .certificatePdf(new byte[] {3, 4})
                .build());

    DownloadEsignArtifactsResponse response =
        esignApi.downloadArtifacts(
            DownloadEsignArtifactsRequest.builder().envelopeId("env-download").build());

    assertThat(response.getSignedDocumentPdf()).containsExactly(1, 2);
    assertThat(response.getCertificatePdf()).containsExactly(3, 4);
    verify(docusignEsignGateway).downloadArtifacts(any(), eq("env-download"));
  }

  private Integer createTenantWithDocusignConfig() {
    Integer tenantId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO tenants (name, active, created_by, created_at, updated_by, updated_at, version)
            VALUES (?, TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0)
            RETURNING id
            """,
            Integer.class,
            "tenant-" + System.nanoTime());
    jdbcTemplate.update(
        """
        INSERT INTO tenant_docusign_configs
          (tenant_id, base_path, account_id, auth_token, active,
           created_by, created_at, updated_by, updated_at, version)
        VALUES (?, ?, ?, ?, TRUE, 1, CURRENT_TIMESTAMP, 1, CURRENT_TIMESTAMP, 0)
        """,
        tenantId,
        "https://demo.docusign.net/restapi",
        "account-1",
        "token-1");
    return tenantId;
  }

  private byte[] createPdfWithAnchors(String text) throws Exception {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage();
      document.addPage(page);
      try (PDPageContentStream content = new PDPageContentStream(document, page)) {
        content.beginText();
        content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        content.newLineAtOffset(50, 700);
        content.showText(text);
        content.endText();
      }
      document.save(out);
      return out.toByteArray();
    }
  }
}
