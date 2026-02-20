package com.gurch.sandbox.requests.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import com.gurch.sandbox.requests.RequestStatus;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RequestAuditingIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private RequestRepository requestRepository;
  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
    requestRepository.deleteAll();
  }

  @Test
  void shouldPopulateCreatedByAndUpdatedByFromAuthenticatedPrincipal() {
    UUID actorId = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO users (id, username, email, enabled, is_system, created_at, updated_at)
        VALUES (:id, :username, :email, true, false, now(), now())
        """,
        Map.of(
            "id", actorId,
            "username", "actor-" + actorId,
            "email", "actor-" + actorId + "@local.invalid"));

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken(actorId.toString(), "n/a"));

    RequestEntity saved =
        requestRepository.save(
            RequestEntity.builder()
                .requestTypeKey("loan")
                .requestTypeVersion(1)
                .payloadJson(new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode())
                .status(RequestStatus.DRAFT)
                .build());

    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT created_by, updated_by
            FROM requests
            WHERE id = :id
            """,
            Map.of("id", saved.getId()));

    assertThat(row.get("created_by")).isEqualTo(actorId);
    assertThat(row.get("updated_by")).isEqualTo(actorId);
  }
}
