package com.gurch.sandbox;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "1")
public abstract class AbstractJdbcIntegrationTest {
  // Shared Spring Boot + Testcontainers context for integration tests.

  @Autowired private JdbcTemplate jdbcTemplate;

  protected List<String> auditActionsFor(String resourceType, String resourceId) {
    return jdbcTemplate.queryForList(
        """
        SELECT action
        FROM audit_log_events
        WHERE resource_type = ?
          AND resource_id = ?
        ORDER BY created_at DESC, id DESC
        """,
        String.class,
        resourceType,
        resourceId);
  }
}
