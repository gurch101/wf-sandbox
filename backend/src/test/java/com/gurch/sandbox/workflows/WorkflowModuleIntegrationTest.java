package com.gurch.sandbox.workflows;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class WorkflowModuleIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldSearchWorkflowDefinitions() throws Exception {
    mockMvc
        .perform(
            get("/api/internal/workflows/process-definitions/search")
                .param("processDefinitionKeyContains", "requestTypeV1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].key").value("requestTypeV1Process"));
  }
}
