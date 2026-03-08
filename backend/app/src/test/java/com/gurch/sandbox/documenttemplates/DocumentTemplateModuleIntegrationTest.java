package com.gurch.sandbox.documenttemplates;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.documenttemplates.internal.DocumentTemplateRepository;
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requests.CreateRequestCommand;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.internal.RequestRepository;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.TenantCommand;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.UserCommand;
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
import org.mockito.Mockito;
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
  @Autowired private RequestApi requestApi;
  @Autowired private RequestTypeApi requestTypeApi;
  @Autowired private RequestRepository requestRepository;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private TenantApi tenantApi;
  @Autowired private UserApi userApi;

  @Value("${storage.local-root}")
  private String storageRoot;

  @BeforeEach
  void setUp() throws IOException {
    repository.deleteAll();
    requestRepository.deleteAll();
    requestTypeRepository.deleteAll();
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
                    .param("description", "Onboarding package")
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
        .andExpect(jsonPath("$.name").value("Client Intake Form.pdf"))
        .andExpect(jsonPath("$.description").value("Onboarding package"))
        .andExpect(jsonPath("$.tenantId").isEmpty())
        .andExpect(jsonPath("$.esignable").value(true))
        .andExpect(jsonPath("$.formMap.fields.length()").value(3))
        .andExpect(jsonPath("$.formMap.fields[0].key").value("clientName"))
        .andExpect(jsonPath("$.formMap.fields[0].type").value("TEXT"));

    mockMvc
        .perform(get("/api/admin/document-templates/{id}/download", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"Client Intake Form.pdf\""))
        .andExpect(content().bytes(payload));

    mockMvc
        .perform(get("/api/admin/document-templates/search").param("nameContains", "intake"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id))
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
        .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail").value("Unsupported file type. Only PDF and DOCX are allowed"));
  }

  @Test
  void shouldRejectUploadLargerThanConfiguredServiceLimit() throws Exception {
    byte[] oversized = new byte[120000];
    MockMultipartFile file =
        new MockMultipartFile("file", "too-large.pdf", "application/pdf", oversized);

    mockMvc
        .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
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
        .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
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
            .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
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
        .andExpect(jsonPath("$.esignable").value(false))
        .andExpect(jsonPath("$.formMap.fields.length()").value(2))
        .andExpect(jsonPath("$.formMap.fields[0].key").value("client.firstName"))
        .andExpect(jsonPath("$.formMap.fields[1].key").value("client.lastName"));
  }

  @Test
  void shouldAcceptPdfWithoutFormFields() throws Exception {
    byte[] payload = createFlatPdf();
    MockMultipartFile file = new MockMultipartFile("file", "flat.pdf", "application/pdf", payload);

    mockMvc
        .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
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
        .perform(multipart("/api/admin/document-templates").file(file).with(csrf()))
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
  void shouldGenerateComposedPdfFromRequestBundleInOrder() throws Exception {
    uploadTemplateWithTenantAndKey(
        "fillable.pdf", "bundle-fillable", "application/pdf", createFillablePdfWithAnchor(), null);
    uploadTemplateWithTenantAndKey(
        "template.docx",
        "bundle-word",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("Client {{client.firstName}} ${client.lastName}"),
        null);

    createRequestTypeWithMappings(
        "bundle-loan",
        List.of(
            Map.of(
                "templateKey",
                "bundle-fillable",
                "fieldBindings",
                Map.of(
                    "clientName", "payload.client.firstName",
                    "consent", "payload.flags.consent",
                    "state", "payload.address.state")),
            Map.of(
                "templateKey",
                "bundle-word",
                "fieldBindings",
                Map.of(
                    "client.firstName", "payload.client.firstName",
                    "client.lastName", "payload.client.lastName"))));

    Long requestOne =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("bundle-loan")
                .payload(
                    objectMapper.valueToTree(
                        Map.of(
                            "client", Map.of("firstName", "Ada", "lastName", "Lovelace"),
                            "flags", Map.of("consent", true),
                            "address", Map.of("state", "NY"))))
                .build());
    Long requestTwo =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("bundle-loan")
                .payload(
                    objectMapper.valueToTree(
                        Map.of(
                            "client", Map.of("firstName", "Grace", "lastName", "Hopper"),
                            "flags", Map.of("consent", true),
                            "address", Map.of("state", "CA"))))
                .build());

    DocumentTemplateDownload output =
        documentTemplateApi.generateFromRequests(
            new DocumentTemplateGenerateFromRequestsRequest(List.of(requestOne, requestTwo)));
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }

    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(4);
      assertThat(text).contains("Ada");
      assertThat(text).contains("Lovelace");
      assertThat(text).contains("Grace");
      assertThat(text).contains("Hopper");
    }
  }

  @Test
  void shouldPreferTenantTemplateOverGlobalForRequestDrivenGeneration() throws Exception {
    Integer tenantId =
        tenantApi.create(
            TenantCommand.builder().name("tenant-docs-" + UUID.randomUUID()).active(true).build());

    uploadTemplateWithTenantAndKey(
        "global.docx",
        "offer-letter",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("GLOBAL {{client.firstName}}"),
        null);
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));
    uploadTemplateWithTenantAndKey(
        "tenant.docx",
        "offer-letter",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("TENANT {{client.firstName}}"),
        tenantId);

    createRequestTypeWithMappings(
        "tenant-mapping",
        List.of(
            Map.of(
                "templateKey",
                "offer-letter",
                "fieldBindings",
                Map.of("client.firstName", "payload.client.firstName"))));

    Long requestId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("tenant-mapping")
                .payload(objectMapper.valueToTree(Map.of("client", Map.of("firstName", "Ada"))))
                .build());

    DocumentTemplateDownload output =
        documentTemplateApi.generateFromRequests(
            new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId)));
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }
    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("TENANT Ada");
      assertThat(text).doesNotContain("GLOBAL Ada");
    }
  }

  @Test
  void shouldFailBundleGenerationWhenRequiredPayloadBindingIsMissing() throws Exception {
    uploadTemplateWithTenantAndKey(
        "template.docx",
        "strict-template",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("Name {{name}}"),
        null);
    createRequestTypeWithMappings(
        "strict-type",
        List.of(
            Map.of(
                "templateKey",
                "strict-template",
                "fieldBindings",
                Map.of("name", "payload.missing.name"))));

    Long requestId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("strict-type")
                .payload(objectMapper.valueToTree(Map.of("name", "Ada")))
                .build());

    assertThatThrownBy(
            () ->
                documentTemplateApi.generateFromRequests(
                    new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Missing required payload path");
  }

  @Test
  void shouldAllowTenantOverrideToMakeDocumentOptional() throws Exception {
    Integer tenantId =
        tenantApi.create(
            TenantCommand.builder()
                .name("tenant-optional-" + UUID.randomUUID())
                .active(true)
                .build());
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId));

    uploadTemplateWithTenantAndKey(
        "base.docx",
        "base-template",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("BASE {{client.firstName}}"),
        tenantId);
    createRequestTypeWithMappings(
        "tenant-optional-type",
        List.of(
            Map.of(
                "templateKey",
                "base-template",
                "fieldBindings",
                Map.of("client.firstName", "payload.client.firstName")),
            Map.of(
                "templateKey",
                "consent-template",
                "required",
                true,
                "tenantRules",
                List.of(Map.of("tenantId", tenantId, "required", false)),
                "fieldBindings",
                Map.of("client.firstName", "payload.client.firstName"))));

    Long requestId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("tenant-optional-type")
                .payload(objectMapper.valueToTree(Map.of("client", Map.of("firstName", "Ada"))))
                .build());

    DocumentTemplateDownload output =
        documentTemplateApi.generateFromRequests(
            new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId)));
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }
    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("BASE Ada");
      assertThat(text).doesNotContain("consent-template");
    }

    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.empty());
    assertThatThrownBy(
            () ->
                documentTemplateApi.generateFromRequests(
                    new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("No template found for key 'consent-template'");
  }

  @Test
  void shouldResolveComputedFieldsViaUserByIdResolver() throws Exception {
    Integer userId =
        userApi.create(
            UserCommand.builder()
                .username("client-" + UUID.randomUUID())
                .email("client-" + UUID.randomUUID() + "@example.com")
                .active(true)
                .tenantId(null)
                .build());

    uploadTemplateWithTenantAndKey(
        "computed.docx",
        "computed-template",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("User {{client.username}} Email {{client.email}}"),
        null);
    createRequestTypeWithMappings(
        "computed-type",
        List.of(
            Map.of(
                "templateKey",
                "computed-template",
                "fieldBindings",
                Map.of(
                    "client.username",
                    Map.of(
                        "resolver",
                        "user-by-id",
                        "inputPath",
                        "payload.clientId",
                        "outputPath",
                        "username"),
                    "client.email",
                    Map.of(
                        "resolver",
                        "user-by-id",
                        "inputPath",
                        "payload.clientId",
                        "outputPath",
                        "email")))));

    Long requestId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("computed-type")
                .payload(objectMapper.valueToTree(Map.of("clientId", userId)))
                .build());

    DocumentTemplateDownload output =
        documentTemplateApi.generateFromRequests(
            new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId)));
    byte[] generated;
    try (java.io.InputStream in = output.getContentStream()) {
      generated = in.readAllBytes();
    }

    try (PDDocument document = Loader.loadPDF(generated)) {
      String text = new PDFTextStripper().getText(document);
      assertThat(text).contains("User");
      assertThat(text).contains(userApi.findById(userId).orElseThrow().getUsername());
      assertThat(text).contains(userApi.findById(userId).orElseThrow().getEmail());
    }
  }

  @Test
  void shouldFailWhenComputedResolverInputEntityIsMissing() throws Exception {
    uploadTemplateWithTenantAndKey(
        "computed.docx",
        "computed-missing-user",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        createTemplatedDocx("User {{client.username}}"),
        null);
    createRequestTypeWithMappings(
        "computed-missing-type",
        List.of(
            Map.of(
                "templateKey",
                "computed-missing-user",
                "fieldBindings",
                Map.of(
                    "client.username",
                    Map.of(
                        "resolver",
                        "user-by-id",
                        "inputPath",
                        "payload.clientId",
                        "outputPath",
                        "username")))));

    Long requestId =
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey("computed-missing-type")
                .payload(objectMapper.valueToTree(Map.of("clientId", 999_999)))
                .build());

    assertThatThrownBy(
            () ->
                documentTemplateApi.generateFromRequests(
                    new DocumentTemplateGenerateFromRequestsRequest(List.of(requestId))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("User not found for id");
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

    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(tenantId + 1));

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
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(1));
    MockMultipartFile file =
        new MockMultipartFile("file", "tenant2.pdf", "application/pdf", createFlatPdf());

    mockMvc
        .perform(
            multipart("/api/admin/document-templates")
                .file(file)
                .param("tenantId", "2")
                .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("tenantId must match the authenticated user's tenant scope"));
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
        contentStream.showText("Please sign here s1 and date d1");
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

  private static byte[] createTemplatedDocx() throws IOException {
    return createTemplatedDocx(
        "Client First Name: {{client.firstName}} | Last Name: ${client.lastName}");
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
    return uploadTemplateWithTenantAndKey(name, null, mimeType, payload, null);
  }

  private Long uploadTemplateWithTenant(
      String name, String mimeType, byte[] payload, Integer tenantId) throws Exception {
    return uploadTemplateWithTenantAndKey(name, null, mimeType, payload, tenantId);
  }

  private Long uploadTemplateWithTenantAndKey(
      String name, String templateKey, String mimeType, byte[] payload, Integer tenantId)
      throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", name, mimeType, payload);
    var builder = multipart("/api/admin/document-templates").file(file).with(csrf());
    if (templateKey != null) {
      builder = builder.param("templateKey", templateKey);
    }
    if (tenantId != null) {
      builder = builder.param("tenantId", String.valueOf(tenantId));
    }
    MvcResult uploadResult = mockMvc.perform(builder).andExpect(status().isCreated()).andReturn();
    return objectMapper
        .readValue(uploadResult.getResponse().getContentAsString(), CreateResponse.class)
        .getId();
  }

  private void createRequestTypeWithMappings(String typeKey, List<Map<String, Object>> documents) {
    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey(typeKey)
            .name(typeKey)
            .description("desc")
            .payloadHandlerId("noop")
            .processDefinitionKey("requestTypeV1Process")
            .configJson(
                objectMapper.valueToTree(
                    Map.of("documentGeneration", Map.of("documents", documents))))
            .build());
  }
}
