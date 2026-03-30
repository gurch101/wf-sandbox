package com.gurch.sandbox.documenttemplates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateDownload;
import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateGenerateRequest;
import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateUpdateRequest;
import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateUploadRequest;
import com.gurch.sandbox.documenttemplates.internal.DocumentTemplateRepository;
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.dto.TenantCommand;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@WithMockUser(username = "1")
class DocumentTemplateModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private DocumentTemplateRepository repository;
  @Autowired private DocumentTemplateApi documentTemplateApi;
  @Autowired private TenantApi tenantApi;

  @Value("${storage.local-root}")
  private String storageRoot;

  @BeforeEach
  void setUp() throws IOException {
    repository.deleteAll();
    Path root = Path.of(storageRoot);
    if (Files.exists(root)) {
      try (Stream<Path> stream = Files.walk(root)) {
        stream.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(java.io.File::delete);
      }
    }
    Files.createDirectories(root);
  }

  @Test
  void shouldUploadDownloadSearchAndDeleteDocumentTemplate() throws Exception {
    byte[] payload = createFillablePdfWithAnchor();
    MockMultipartFile file =
        new MockMultipartFile("file", "Client Intake Form.pdf", "application/pdf", payload);

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/admin/document-templates")
                    .file(file)
                    .file(
                        requestPart(
                            new DocumentTemplateUploadRequest(
                                "Client Intake Form.pdf",
                                "Formulaire d'accueil client",
                                "Onboarding package",
                                "Dossier d'integration",
                                DocumentTemplateLanguage.FRENCH,
                                null)))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").exists())
            .andReturn();

    Long id =
        objectMapper
            .readValue(uploadResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    mockMvc
        .perform(get("/api/admin/document-templates/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.enName").value("Client Intake Form.pdf"))
        .andExpect(jsonPath("$.frName").value("Formulaire d'accueil client"))
        .andExpect(jsonPath("$.enDescription").value("Onboarding package"))
        .andExpect(jsonPath("$.frDescription").value("Dossier d'integration"))
        .andExpect(jsonPath("$.language").value("FRENCH"))
        .andExpect(jsonPath("$.tenantId").isEmpty())
        .andExpect(jsonPath("$.esignable").value(true))
        .andExpect(jsonPath("$.esignAnchorMetadata.signatureAnchorKeys.length()").value(1))
        .andExpect(jsonPath("$.esignAnchorMetadata.signatureAnchorKeys[0]").value("s1"))
        .andExpect(jsonPath("$.esignAnchorMetadata.dateAnchorKeys.length()").value(1))
        .andExpect(jsonPath("$.esignAnchorMetadata.dateAnchorKeys[0]").value("d1"))
        .andExpect(jsonPath("$.formMap.fields.length()").value(3))
        .andExpect(jsonPath("$.formMap.fields[0].key").value("clientName"))
        .andExpect(jsonPath("$.formMap.fields[0].type").value("TEXT"));

    mockMvc
        .perform(get("/api/admin/document-templates/{id}/download", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(header().longValue("Content-Length", payload.length))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"Client Intake Form.pdf\""));

    mockMvc
        .perform(get("/api/admin/document-templates/search").param("nameBegins", "Form"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
        .andExpect(jsonPath("$.items[0].language").value("FRENCH"))
        .andExpect(jsonPath("$.items[0].frName").value("Formulaire d'accueil client"))
        .andExpect(jsonPath("$.items[0].tenantId").isEmpty());

    mockMvc
        .perform(
            delete("/api/admin/document-templates/{id}", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/admin/document-templates/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Document template not found with id: " + id));

    assertThat(auditActionsFor("documenttemplates", id.toString()))
        .containsExactly("DELETE", "CREATE");
  }

  @Test
  void shouldRejectUnsupportedUploadType() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "script.txt",
            MediaType.TEXT_PLAIN_VALUE,
            "bad".getBytes(StandardCharsets.UTF_8));

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "script.txt",
                            null,
                            null,
                            null,
                            DocumentTemplateLanguage.ENGLISH,
                            null)))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].code").value("UNSUPPORTED_FILE_TYPE"));
  }

  @Test
  void shouldRejectUploadLargerThanConfiguredServiceLimit() throws Exception {
    byte[] oversized = new byte[120000];
    MockMultipartFile file =
        new MockMultipartFile("file", "too-large.pdf", "application/pdf", oversized);

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "too-large.pdf",
                            null,
                            null,
                            null,
                            DocumentTemplateLanguage.ENGLISH,
                            null)))
                .with(csrf()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.title").value("Payload Too Large"))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void shouldRejectUploadLargerThanMultipartLimit() throws Exception {
    byte[] oversized = new byte[250000];
    MockMultipartFile file =
        new MockMultipartFile("file", "too-large-for-multipart.pdf", "application/pdf", oversized);

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "too-large-for-multipart.pdf",
                            null,
                            null,
                            null,
                            DocumentTemplateLanguage.ENGLISH,
                            null)))
                .with(csrf()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.title").value("Payload Too Large"))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void shouldUploadTemplatedWordDocumentAndExtractFieldMap() throws Exception {
    byte[] payload = createTemplatedDocx();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "Client Intake Template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            payload);

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/admin/document-templates")
                    .file(file)
                    .file(
                        requestPart(
                            new DocumentTemplateUploadRequest(
                                "Client Intake Template.docx",
                                null,
                                null,
                                null,
                                DocumentTemplateLanguage.ENGLISH,
                                null)))
                    .with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();

    Long id =
        objectMapper
            .readValue(uploadResult.getResponse().getContentAsString(), CreateResponse.class)
            .getId();

    mockMvc
        .perform(get("/api/admin/document-templates/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tenantId").isEmpty())
        .andExpect(jsonPath("$.language").value("ENGLISH"))
        .andExpect(jsonPath("$.esignable").value(false))
        .andExpect(jsonPath("$.formMap.fields.length()").value(2))
        .andExpect(jsonPath("$.formMap.fields[0].key").value("client.firstName"))
        .andExpect(jsonPath("$.formMap.fields[1].key").value("client.lastName"));
  }

  @Test
  void shouldMarkUploadedDocxFixtureWithEsignAnchorsAsEsignable() throws Exception {
    Long id =
        uploadTemplate(
            "introspect.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            loadTestResource("documenttemplates/introspect.docx"));

    mockMvc
        .perform(get("/api/admin/document-templates/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.esignable").value(true));
  }

  @Test
  void shouldAcceptPdfWithoutFormFields() throws Exception {
    byte[] payload = createFlatPdf();
    MockMultipartFile file = new MockMultipartFile("file", "flat.pdf", "application/pdf", payload);

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "flat.pdf", null, null, null, DocumentTemplateLanguage.ENGLISH, null)))
                .with(csrf()))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldAcceptWordWithoutTemplatePlaceholders() throws Exception {
    byte[] payload = createNonTemplatedDocx();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "plain.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            payload);

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "plain.docx",
                            null,
                            null,
                            null,
                            DocumentTemplateLanguage.ENGLISH,
                            null)))
                .with(csrf()))
        .andExpect(status().isCreated());
  }

  @Test
  void shouldGenerateComposedPdfFromPdfAndWordTemplates() throws Exception {
    Long pdfId = uploadTemplate("fillable.pdf", "application/pdf", createFillablePdfWithAnchor());
    Long docxId =
        uploadTemplate(
            "template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createTemplatedDocx());

    DocumentTemplateGenerateRequest request =
        new DocumentTemplateGenerateRequest(
            List.of(
                new DocumentTemplateGenerateRequest.GenerateInput(
                    pdfId, java.util.Map.of("clientName", "Ada", "consent", "true", "state", "NY")),
                new DocumentTemplateGenerateRequest.GenerateInput(
                    docxId,
                    java.util.Map.of("client.firstName", "Ada", "client.lastName", "Lovelace"))));

    DocumentTemplateDownload output = documentTemplateApi.generate(request);
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }

    assertThat(output.getMimeType()).isEqualTo("application/pdf");
    assertThat(generated).isNotEmpty();

    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(2);
      assertThat(text).contains("Ada");
      assertThat(text).contains("Lovelace");
      assertThat(document.getDocumentCatalog().getAcroForm())
          .satisfies(
              acroForm -> {
                if (acroForm != null) {
                  assertThat(acroForm.getFieldTree().iterator().hasNext()).isFalse();
                }
              });
    }
  }

  @Test
  void shouldGenerateComposedPdfViaEndpoint() throws Exception {
    Long pdfId = uploadTemplate("fillable.pdf", "application/pdf", createFillablePdfWithAnchor());
    Long docxId =
        uploadTemplate(
            "template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            createTemplatedDocx());

    DocumentTemplateGenerateRequest request =
        new DocumentTemplateGenerateRequest(
            List.of(
                new DocumentTemplateGenerateRequest.GenerateInput(
                    pdfId, java.util.Map.of("clientName", "Ada", "consent", "true", "state", "NY")),
                new DocumentTemplateGenerateRequest.GenerateInput(
                    docxId,
                    java.util.Map.of("client.firstName", "Ada", "client.lastName", "Lovelace"))));

    byte[] generated =
        mockMvc
            .perform(
                post("/api/admin/document-templates/generate")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsBytes(request)))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Type", "application/pdf"))
            .andExpect(
                header()
                    .string(
                        "Content-Disposition",
                        "attachment; filename=\"generated-document-bundle.pdf\""))
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Ada");
      assertThat(text).contains("Lovelace");
    }
  }

  @Test
  void shouldRejectGenerateRequestWithoutDocuments() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/document-templates/generate")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"documents\":[]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].name").value("documents"));
  }

  @Test
  void shouldUpdateMetadataAndTemplateWhenFieldMapIsUnchanged() throws Exception {
    Long templateId =
        uploadTemplate("fillable.pdf", "application/pdf", createFillablePdfWithAnchor());
    MockMultipartFile updatedFile =
        new MockMultipartFile(
            "file", "fillable-v2.pdf", "application/pdf", createFillablePdfWithAnchor());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates/{id}", templateId)
                .file(updatedFile)
                .file(
                    requestPart(
                        new DocumentTemplateUpdateRequest(
                            "Updated Form Name",
                            "Nom mis a jour",
                            "Updated description",
                            "Description mise a jour")))
                .with(csrf())
                .with(
                    request -> {
                      request.setMethod("PATCH");
                      return request;
                    }))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(templateId))
        .andExpect(jsonPath("$.enName").value("Updated Form Name"))
        .andExpect(jsonPath("$.frName").value("Nom mis a jour"))
        .andExpect(jsonPath("$.enDescription").value("Updated description"))
        .andExpect(jsonPath("$.frDescription").value("Description mise a jour"))
        .andExpect(jsonPath("$.language").value("ENGLISH"))
        .andExpect(jsonPath("$.esignable").value(true))
        .andExpect(jsonPath("$.formMap.fields.length()").value(3));
  }

  @Test
  void shouldRejectUpdateWhenReplacementChangesFieldMap() throws Exception {
    Long templateId =
        uploadTemplate("fillable.pdf", "application/pdf", createFillablePdfWithAnchor());
    MockMultipartFile changedFile =
        new MockMultipartFile("file", "flat.pdf", "application/pdf", createFlatPdf());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates/{id}", templateId)
                .file(changedFile)
                .file(requestPart(new DocumentTemplateUpdateRequest(null, null, null, null)))
                .with(csrf())
                .with(
                    request -> {
                      request.setMethod("PATCH");
                      return request;
                    }))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].code").value("TEMPLATE_FIELD_MAP_CHANGED"));
  }

  @Test
  void shouldRejectUpdateWhenReplacementChangesEsignAnchorMetadata() throws Exception {
    Long templateId =
        uploadTemplate("fillable.pdf", "application/pdf", createFillablePdfWithAnchor());
    MockMultipartFile changedFile =
        new MockMultipartFile(
            "file", "fillable-v2.pdf", "application/pdf", createFillablePdfWithDifferentAnchor());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates/{id}", templateId)
                .file(changedFile)
                .file(requestPart(new DocumentTemplateUpdateRequest(null, null, null, null)))
                .with(csrf())
                .with(
                    request -> {
                      request.setMethod("PATCH");
                      return request;
                    }))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].code").value("TEMPLATE_ESIGN_ANCHORS_CHANGED"));
  }

  @Test
  void shouldRenderDocxTableWithMultipleHoldingsRows() throws Exception {
    Long docxId =
        uploadTemplate(
            "holdings-template.docx",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            loadTestResource("documenttemplates/introspect.docx"));

    DocumentTemplateGenerateRequest request =
        new DocumentTemplateGenerateRequest(
            List.of(
                new DocumentTemplateGenerateRequest.GenerateInput(
                    docxId,
                    Map.of(
                        "clientName",
                        "Ada Lovelace",
                        "holdings",
                        List.of(
                            Map.of("name", "AAPL", "quantity", 10, "marketValue", "1870.00"),
                            Map.of("name", "MSFT", "quantity", 5, "marketValue", "2060.00"))))));

    DocumentTemplateDownload output = documentTemplateApi.generate(request);
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }

    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("Ada Lovelace");
      assertThat(text).contains("Security Name");
      assertThat(text).contains("Quantity");
      assertThat(text).contains("Market Value");
      assertThat(text).contains("AAPL");
      assertThat(text).contains("MSFT");
      assertThat(text).contains("1870.00");
      assertThat(text).contains("2060.00");
    }
  }

  @Test
  void shouldRestrictReadDownloadDeleteAndSearchToUsersTenantScope() throws Exception {
    Integer tenantId =
        tenantApi.create(
            TenantCommand.builder()
                .name("tenant-access-test-" + UUID.randomUUID())
                .active(true)
                .build());
    Long tenantTwoTemplateId =
        uploadTemplateWithTenant("tenant.pdf", "application/pdf", createFlatPdf(), tenantId);
    uploadTemplateWithTenant("global.pdf", "application/pdf", createFlatPdf(), null);

    org.mockito.Mockito.when(currentUserProvider.currentTenantId())
        .thenReturn(Optional.of(tenantId + 1));

    mockMvc
        .perform(get("/api/admin/document-templates/{id}", tenantTwoTemplateId))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/admin/document-templates/{id}/download", tenantTwoTemplateId))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(
            delete("/api/admin/document-templates/{id}", tenantTwoTemplateId)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(csrf()))
        .andExpect(status().isNotFound());

    mockMvc
        .perform(get("/api/admin/document-templates/search"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].tenantId").isEmpty());
  }

  @Test
  void shouldRejectUploadWhenTenantIdDoesNotMatchUsersTenant() throws Exception {
    org.mockito.Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(1));
    MockMultipartFile file =
        new MockMultipartFile("file", "tenant2.pdf", "application/pdf", createFlatPdf());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    requestPart(
                        new DocumentTemplateUploadRequest(
                            "tenant2.pdf", null, null, null, DocumentTemplateLanguage.ENGLISH, 2)))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Request has invalid fields"))
        .andExpect(jsonPath("$.errors[0].code").value("TENANT_SCOPE_MISMATCH"));
  }

  @Test
  void shouldRejectUploadWhenLanguageIsInvalid() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile("file", "bad-language.pdf", "application/pdf", createFlatPdf());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .file(
                    new MockMultipartFile(
                        "request",
                        "",
                        MediaType.APPLICATION_JSON_VALUE,
                        """
                        {
                          "enName": "bad-language.pdf",
                          "language": "spanish"
                        }
                        """
                            .getBytes(StandardCharsets.UTF_8)))
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Validation Failed"));
  }

  private static byte[] createFillablePdfWithAnchor() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);

      PDAcroForm form = new PDAcroForm(document);
      document.getDocumentCatalog().setAcroForm(form);
      form.setDefaultAppearance("/Helv 10 Tf 0 g");

      try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 760);
        contentStream.showText("Please sign here /s1/ and date /d1/");
        contentStream.endText();
      }

      PDTextField clientName = new PDTextField(form);
      clientName.setPartialName("clientName");
      form.getFields().add(clientName);
      attachWidget(clientName.getWidgets().get(0), page, 50, 700, 200, 18);

      PDCheckBox consent = new PDCheckBox(form);
      consent.setPartialName("consent");
      form.getFields().add(consent);
      attachWidget(consent.getWidgets().get(0), page, 50, 665, 18, 18);

      PDComboBox state = new PDComboBox(form);
      state.setPartialName("state");
      state.setOptions(java.util.List.of("CA", "NY"));
      form.getFields().add(state);
      attachWidget(state.getWidgets().get(0), page, 50, 630, 120, 18);

      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] createFlatPdf() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);
      try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 760);
        contentStream.showText("This is not a form PDF");
        contentStream.endText();
      }
      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] createFillablePdfWithDifferentAnchor() throws IOException {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      PDPage page = new PDPage(PDRectangle.LETTER);
      document.addPage(page);

      PDAcroForm form = new PDAcroForm(document);
      document.getDocumentCatalog().setAcroForm(form);
      form.setDefaultAppearance("/Helv 10 Tf 0 g");

      try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
        contentStream.newLineAtOffset(50, 760);
        contentStream.showText("Please sign here /s2/ and date /d2/");
        contentStream.endText();
      }

      PDTextField clientName = new PDTextField(form);
      clientName.setPartialName("clientName");
      form.getFields().add(clientName);
      attachWidget(clientName.getWidgets().get(0), page, 50, 700, 200, 18);

      PDCheckBox consent = new PDCheckBox(form);
      consent.setPartialName("consent");
      form.getFields().add(consent);
      attachWidget(consent.getWidgets().get(0), page, 50, 665, 18, 18);

      PDComboBox state = new PDComboBox(form);
      state.setPartialName("state");
      state.setOptions(java.util.List.of("CA", "NY"));
      form.getFields().add(state);
      attachWidget(state.getWidgets().get(0), page, 50, 630, 120, 18);

      document.save(out);
      return out.toByteArray();
    }
  }

  private static byte[] createTemplatedDocx() throws IOException {
    return createTemplatedDocx(
        "Client First Name: {{client.firstName}} | Last Name: {{client.lastName}}");
  }

  private static byte[] createTemplatedDocx(String templateText) throws IOException {
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph paragraph = document.createParagraph();
      XWPFRun run = paragraph.createRun();
      run.setText(templateText);
      document.write(out);
      return out.toByteArray();
    }
  }

  private static byte[] createNonTemplatedDocx() throws IOException {
    try (XWPFDocument document = new XWPFDocument();
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      XWPFParagraph paragraph = document.createParagraph();
      paragraph.createRun().setText("This document has no template placeholders.");
      document.write(out);
      return out.toByteArray();
    }
  }

  private static void attachWidget(
      PDAnnotationWidget widget, PDPage page, float x, float y, float width, float height)
      throws IOException {
    widget.setRectangle(new PDRectangle(x, y, width, height));
    widget.setPage(page);
    page.getAnnotations().add(widget);
  }

  private Long uploadTemplate(String name, String mimeType, byte[] payload) throws Exception {
    return uploadTemplateWithTenant(name, mimeType, payload, null);
  }

  private static byte[] loadTestResource(String path) throws IOException {
    try (var inputStream =
        DocumentTemplateModuleIntegrationTest.class.getClassLoader().getResourceAsStream(path)) {
      assertThat(inputStream).as("Missing test resource: %s", path).isNotNull();
      return inputStream.readAllBytes();
    }
  }

  private Long uploadTemplateWithTenant(
      String name, String mimeType, byte[] payload, Integer tenantId) throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", name, mimeType, payload);
    var builder =
        multipart("/api/admin/document-templates")
            .file(file)
            .file(
                requestPart(
                    new DocumentTemplateUploadRequest(
                        name, null, null, null, DocumentTemplateLanguage.ENGLISH, tenantId)))
            .with(csrf());
    MvcResult uploadResult = mockMvc.perform(builder).andExpect(status().isCreated()).andReturn();
    return objectMapper
        .readValue(uploadResult.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }

  private MockMultipartFile requestPart(Object request) throws Exception {
    return new MockMultipartFile(
        "request", "", MediaType.APPLICATION_JSON_VALUE, objectMapper.writeValueAsBytes(request));
  }
}
