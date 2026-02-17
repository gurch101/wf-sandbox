package com.gurch.sandbox.forms;

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
import com.gurch.sandbox.forms.internal.FormFileRepository;
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
@WithMockUser
class FormFileModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private FormFileRepository repository;

  @Value("${forms.storage.local-root}")
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
  void shouldUploadDownloadSearchAndDeleteFormFile() throws Exception {
    byte[] payload = "sample pdf bytes".getBytes(StandardCharsets.UTF_8);
    MockMultipartFile file =
        new MockMultipartFile("file", "Client Intake Form.pdf", "application/pdf", payload);

    MvcResult uploadResult =
        mockMvc
            .perform(
                multipart("/api/forms/files")
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
        .perform(get("/api/forms/files/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Client Intake Form.pdf"))
        .andExpect(jsonPath("$.description").value("Onboarding package"))
        .andExpect(jsonPath("$.documentType").value("PDF_FORM"))
        .andExpect(jsonPath("$.signatureStatus").value("NOT_REQUESTED"));

    mockMvc
        .perform(get("/api/forms/files/{id}/download", id))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/pdf"))
        .andExpect(
            header()
                .string("Content-Disposition", "attachment; filename=\"Client Intake Form.pdf\""))
        .andExpect(content().bytes(payload));

    mockMvc
        .perform(get("/api/forms/files/search").param("nameContains", "intake"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.files.length()").value(1))
        .andExpect(jsonPath("$.files[0].id").value(id));

    mockMvc
        .perform(
            delete("/api/forms/files/{id}", id)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .with(csrf()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(get("/api/forms/files/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.detail").value("Form file not found with id: " + id));
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
        .perform(multipart("/api/forms/files").file(file).with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.detail")
                .value("Unsupported file type. Only PDF and Word documents are allowed"));
  }
}
