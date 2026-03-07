package com.gurch.sandbox.forms;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.forms.internal.DocumentTemplateRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
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
    byte[] payload = "sample pdf bytes".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file =
        new MockMultipartFile("file", "Client Intake Form.pdf", "application/pdf", payload);

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/document-templates")
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
        .perform(get("/api/document-templates/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Client Intake Form.pdf"))
        .andExpect(jsonPath("$.description").value("Onboarding package"))
        .andExpect(jsonPath("$.documentType").value("PDF_FORM"));

    mockMvc
        .perform(get("/api/document-templates/{id}/download", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"Client Intake Form.pdf\""))
        .andExpect(content().bytes(payload));

    mockMvc
        .perform(get("/api/document-templates/search").param("nameContains", "intake"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(id));

    mockMvc
        .perform(
            delete("/api/document-templates/{id}", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/document-templates/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Document template not found with id: " + id));

    assertThat(auditActionsFor("forms", id.toString())).containsExactly("DELETE", "CREATE");
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
        .perform(multipart("/api/document-templates").file(file).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Unsupported file type. Only PDF and Word documents are allowed"));
  }

  @Test
  void shouldRejectUploadLargerThanConfiguredServiceLimit() throws Exception {
    byte[] oversized = new byte[1500];
    MockMultipartFile file =
        new MockMultipartFile("file", "too-large.pdf", "application/pdf", oversized);

    mockMvc
        .perform(multipart("/api/document-templates").file(file).with(csrf()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.title").value("Payload Too Large"))
        .andExpect(jsonPath("$.detail").exists());
  }

  @Test
  void shouldRejectUploadLargerThanMultipartLimit() throws Exception {
    byte[] oversized = new byte[3000];
    MockMultipartFile file =
        new MockMultipartFile("file", "too-large-for-multipart.pdf", "application/pdf", oversized);

    mockMvc
        .perform(multipart("/api/document-templates").file(file).with(csrf()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.title").value("Payload Too Large"))
        .andExpect(jsonPath("$.detail").exists());
  }
}
