package com.gurch.sandbox.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.idempotency.internal.IdempotencyCleanupTask;
import com.gurch.sandbox.idempotency.internal.IdempotencyRecordEntity;
import com.gurch.sandbox.idempotency.internal.IdempotencyRepository;
import com.gurch.sandbox.idempotency.internal.IdempotencyStatus;
import com.gurch.sandbox.requests.RequestDtos;
import com.gurch.sandbox.requests.RequestStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
@WithMockUser
class IdempotencyIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IdempotencyRepository repository;
  @Autowired private IdempotencyCleanupTask cleanupTask;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void shouldCleanupOldRecords() {
    // Save normally first
    IdempotencyRecordEntity oldRecord =
        repository.save(
            IdempotencyRecordEntity.builder()
                .idempotencyKey("old-key")
                .operation("POST /test")
                .requestHash("hash")
                .status(IdempotencyStatus.COMPLETED)
                .build());

    // Manually backdate it
    jdbcTemplate.update(
        "UPDATE api_idempotency_records SET created_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)),
        oldRecord.getId());

    // Record from now
    repository.save(
        IdempotencyRecordEntity.builder()
            .idempotencyKey("new-key")
            .operation("POST /test")
            .requestHash("hash")
            .status(IdempotencyStatus.COMPLETED)
            .build());

    cleanupTask.cleanupOldRecords();

    assertThat(repository.findByIdempotencyKeyAndOperation("old-key", "POST /test")).isEmpty();
    assertThat(repository.findByIdempotencyKeyAndOperation("new-key", "POST /test")).isPresent();
  }

  @Test
  void shouldReturnSameResponseForSameIdempotencyKey() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    RequestDtos.CreateRequest request =
        new RequestDtos.CreateRequest("Test Request", RequestStatus.DRAFT);

    // First request
    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    String firstResponse = firstResult.getResponse().getContentAsString();

    // Second request (retry)
    MvcResult secondResult =
        mockMvc
            .perform(
                post("/api/requests")
                    .with(csrf())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    String secondResponse = secondResult.getResponse().getContentAsString();

    assertThat(firstResponse).isEqualTo(secondResponse);
    assertThat(firstResponse).isNotEmpty();
  }

  @Test
  void shouldReturnConflictForSameIdempotencyKeyWithDifferentPayload() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();

    RequestDtos.CreateRequest request1 =
        new RequestDtos.CreateRequest("Test Request 1", RequestStatus.DRAFT);

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
        .andExpect(status().isCreated());

    RequestDtos.CreateRequest request2 =
        new RequestDtos.CreateRequest("Test Request 2", RequestStatus.DRAFT);

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturnConflictWhenAlreadyProcessing() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    String operation = "POST /api/requests";

    RequestDtos.CreateRequest request =
        new RequestDtos.CreateRequest("Test Request", RequestStatus.DRAFT);

    // Manually insert a PROCESSING record
    repository.save(
        IdempotencyRecordEntity.builder()
            .idempotencyKey(idempotencyKey)
            .operation(operation)
            .requestHash(calculateHash(request))
            .status(IdempotencyStatus.PROCESSING)
            .build());

    mockMvc
        .perform(
            post("/api/requests")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  private String calculateHash(Object payload) throws Exception {
    byte[] bytes = objectMapper.writeValueAsBytes(payload);
    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(bytes);
    return java.util.Base64.getEncoder().encodeToString(hash);
  }
}
