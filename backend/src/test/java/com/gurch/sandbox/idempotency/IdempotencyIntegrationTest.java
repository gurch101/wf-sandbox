package com.gurch.sandbox.idempotency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.idempotency.internal.IdempotencyCleanupTask;
import com.gurch.sandbox.idempotency.internal.IdempotencyRecordEntity;
import com.gurch.sandbox.idempotency.internal.IdempotencyRepository;
import com.gurch.sandbox.idempotency.internal.IdempotencyStatus;
import com.gurch.sandbox.requests.RequestDtos;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.internal.RequestTypeRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WithMockUser(authorities = {"request.write"})
class IdempotencyIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private IdempotencyRepository repository;
  @Autowired private IdempotencyCleanupTask cleanupTask;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
  @Autowired private RequestTypeRepository requestTypeRepository;
  @Autowired private RequestTypeApi requestTypeApi;

  @BeforeEach
  void setUp() {
    requestTypeRepository.deleteAll();
    requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey("loan")
            .name("Loan")
            .description("desc")
            .payloadHandlerId("amount-positive")
            .processDefinitionKey("requestTypeV1Process")
            .build());
  }

  @Test
  void shouldCleanupOldRecords() {
    IdempotencyRecordEntity oldRecord =
        repository.save(
            IdempotencyRecordEntity.builder()
                .idempotencyKey("old-key")
                .operation("POST /test")
                .requestHash("hash")
                .status(IdempotencyStatus.COMPLETED)
                .build());

    jdbcTemplate.update(
        "UPDATE api_idempotency_records SET created_at = ? WHERE id = ?",
        java.sql.Timestamp.from(Instant.now().minus(31, ChronoUnit.DAYS)),
        oldRecord.getId());

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
        new RequestDtos.CreateRequest("loan", objectMapper.readTree("{\"amount\":99}"));

    MvcResult firstResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
                    .with(csrf())
                    .header("Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    String firstResponse = firstResult.getResponse().getContentAsString();

    MvcResult secondResult =
        mockMvc
            .perform(
                post("/api/requests/drafts")
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
        new RequestDtos.CreateRequest("loan", objectMapper.readTree("{\"amount\":10}"));

    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
        .andExpect(status().isCreated());

    RequestDtos.CreateRequest request2 =
        new RequestDtos.CreateRequest("loan", objectMapper.readTree("{\"amount\":20}"));

    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldReturnConflictWhenAlreadyProcessing() throws Exception {
    String idempotencyKey = UUID.randomUUID().toString();
    String operation = "POST /api/requests/drafts";

    RequestDtos.CreateRequest request =
        new RequestDtos.CreateRequest("loan", objectMapper.readTree("{\"amount\":10}"));

    repository.save(
        IdempotencyRecordEntity.builder()
            .idempotencyKey(idempotencyKey)
            .operation(operation)
            .requestHash(calculateHash(request))
            .status(IdempotencyStatus.PROCESSING)
            .build());

    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isConflict());
  }

  @Test
  void shouldPopulateIdempotencyAuditColumnsFromAuthenticatedPrincipal() throws Exception {
    UUID actorId = UUID.fromString("56565656-5656-5656-5656-565656565656");
    namedParameterJdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        ON CONFLICT (id) DO NOTHING
        """,
        Map.of(
            "id",
            actorId,
            "username",
            actorId.toString(),
            "email",
            "idempotency-audit@local.invalid"));

    String idempotencyKey = UUID.randomUUID().toString();
    mockMvc
        .perform(
            post("/api/requests/drafts")
                .with(
                    user(actorId.toString())
                        .authorities(new SimpleGrantedAuthority("request.write")))
                .with(csrf())
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    objectMapper.writeValueAsString(
                        new RequestDtos.CreateRequest(
                            "loan", objectMapper.readTree("{\"amount\":25}")))))
        .andExpect(status().isCreated());

    Map<String, Object> row =
        namedParameterJdbcTemplate.queryForMap(
            """
            SELECT created_by, updated_by
            FROM api_idempotency_records
            WHERE idempotency_key = :idempotencyKey
              AND operation = :operation
            """,
            Map.of("idempotencyKey", idempotencyKey, "operation", "POST /api/requests/drafts"));
    assertThat(row.get("created_by")).isEqualTo(actorId);
    assertThat(row.get("updated_by")).isEqualTo(actorId);
  }

  private String calculateHash(Object payload) throws Exception {
    byte[] bytes = objectMapper.writeValueAsBytes(payload);
    java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
    byte[] hash = digest.digest(bytes);
    return java.util.Base64.getEncoder().encodeToString(hash);
  }
}
