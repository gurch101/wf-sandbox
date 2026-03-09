package com.gurch.sandbox.esign;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class EsignWebhookControllerIntegrationTest extends AbstractJdbcIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void shouldAcceptWebhookStatusUpdate() throws Exception {
    mockMvc
        .perform(
            post("/api/webhooks/docusign/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-DocuSign-Signature-1", "sig")
                .content(
                    objectMapper.writeValueAsString(
                        new EsignWebhookController.WebhookStatusRequest(
                            "evt-1", "env-1", "completed", java.time.Instant.now()))))
        .andExpect(status().isAccepted());
  }

  @Test
  void shouldRejectInvalidWebhookPayload() throws Exception {
    mockMvc
        .perform(
            post("/api/webhooks/docusign/status")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"\",\"envelopeId\":\"env-1\",\"status\":\"\"}"))
        .andExpect(status().isBadRequest());
  }
}
