package com.gurch.sandbox.esign.internal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EsignReconciliationIntegrationTest extends AbstractEsignIntegrationTest {

  @Test
  void shouldBatchReconcileActiveEnvelopesFromProviderStatus() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(
        existing.toBuilder()
            .status(EsignEnvelopeStatus.SENT)
            .voidedReason(null)
            .lastProviderUpdateAt(null)
            .completedAt(null)
            .build());

    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList()))
        .thenReturn(
            List.of(
                new EnvelopeStatusResult(
                    existing.getExternalEnvelopeId(),
                    EsignEnvelopeStatus.VOIDED,
                    Instant.parse("2026-03-27T10:00:00Z"),
                    "Corrected outside app")));

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeEnvelopeCount").value(1))
        .andExpect(jsonPath("$.providerEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeIds[0]").value(id));

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("VOIDED"))
        .andExpect(jsonPath("$.voidedReason").value("Corrected outside app"))
        .andExpect(jsonPath("$.signers[0].status").value("VOIDED"));
  }

  @Test
  void shouldRejectReconcileForTenantScopedUser() throws Exception {
    Mockito.when(currentUserProvider.currentTenantId()).thenReturn(Optional.of(1));

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isForbidden());

    Mockito.verify(docuSignGateway, Mockito.never()).listEnvelopeStatuses(Mockito.anyList());
  }

  @Test
  void shouldBatchProviderStatusRequestsDuringReconcile() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();
    envelopeRepository.save(
        existing.toBuilder()
            .status(EsignEnvelopeStatus.SENT)
            .lastProviderUpdateAt(null)
            .completedAt(null)
            .build());

    for (int i = 0; i < 100; i++) {
      envelopeRepository.save(
          existing.toBuilder()
              .id(null)
              .createdBy(null)
              .createdAt(null)
              .updatedBy(null)
              .updatedAt(null)
              .version(null)
              .externalEnvelopeId("stub-envelope-id-" + i)
              .status(EsignEnvelopeStatus.SENT)
              .lastProviderUpdateAt(null)
              .completedAt(null)
              .build());
    }

    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList())).thenReturn(List.of());

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeEnvelopeCount").value(101))
        .andExpect(jsonPath("$.providerEnvelopeCount").value(0))
        .andExpect(jsonPath("$.reconciledEnvelopeCount").value(0));

    Mockito.verify(docuSignGateway).listEnvelopeStatuses(Mockito.argThat(ids -> ids.size() == 100));
    Mockito.verify(docuSignGateway).listEnvelopeStatuses(Mockito.argThat(ids -> ids.size() == 1));
  }

  @Test
  void shouldBatchReconcileActiveEnvelopeToDeclined() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();

    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList()))
        .thenReturn(
            List.of(
                new EnvelopeStatusResult(
                    existing.getExternalEnvelopeId(),
                    EsignEnvelopeStatus.DECLINED,
                    Instant.parse("2026-03-27T12:15:00Z"),
                    null)));

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeIds[0]").value(id));

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"));
  }

  @Test
  void shouldMarkSignersCompletedWhenReconcileFindsCompletedEnvelope() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();

    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList()))
        .thenReturn(
            List.of(
                new EnvelopeStatusResult(
                    existing.getExternalEnvelopeId(),
                    EsignEnvelopeStatus.COMPLETED,
                    Instant.parse("2026-03-27T12:15:00Z"),
                    null)));

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeIds[0]").value(id));

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("COMPLETED"))
        .andExpect(jsonPath("$.signers[0].status").value("COMPLETED"));
  }

  @Test
  void shouldNotCountTimestampOnlyRefreshAsReconciledChange() throws Exception {
    Long id = createRemoteEnvelope();
    var existing = envelopeRepository.findById(id).orElseThrow();

    Mockito.when(docuSignGateway.listEnvelopeStatuses(Mockito.anyList()))
        .thenReturn(
            List.of(
                new EnvelopeStatusResult(
                    existing.getExternalEnvelopeId(),
                    EsignEnvelopeStatus.SENT,
                    Instant.parse("2026-03-29T02:35:00Z"),
                    null)));

    mockMvc
        .perform(
            post("/api/esign/reconcile").header("Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeEnvelopeCount").value(1))
        .andExpect(jsonPath("$.providerEnvelopeCount").value(1))
        .andExpect(jsonPath("$.reconciledEnvelopeCount").value(0));

    mockMvc
        .perform(get("/api/esign/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SENT"))
        .andExpect(jsonPath("$.signers[0].status").value("SENT"));
  }
}
