package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignReconciliationApi;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.dto.EsignReconcileResponse;
import com.gurch.sandbox.esign.internal.models.EsignEnvelopeEntity;
import com.gurch.sandbox.esign.internal.models.EsignSignerEntity;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DefaultEsignReconciliationService implements EsignReconciliationApi {

  private static final int RECONCILE_PROVIDER_BATCH_SIZE = 100;

  private final EsignEnvelopeRepository envelopeRepository;
  private final EsignSignerRepository signerRepository;
  private final EsignEnvelopeSupport envelopeSupport;
  private final DocuSignGateway docuSignGateway;
  private final CurrentUserProvider currentUserProvider;
  private final TransactionTemplate transactionTemplate;

  @Override
  public EsignReconcileResponse reconcileActiveEnvelopes() {
    enforceSystemScopeForReconcile();

    List<EsignEnvelopeEntity> activeEnvelopes = loadActiveEnvelopes();
    if (activeEnvelopes.isEmpty()) {
      return new EsignReconcileResponse(0, 0, 0, List.of());
    }

    Map<Long, List<EsignSignerEntity>> signersByEnvelopeId =
        loadSignersByEnvelopeId(activeEnvelopes);
    Map<String, EnvelopeStatusResult> providerStatusesByExternalId =
        loadProviderStatusesByExternalId(activeEnvelopes);
    List<ReconcilePlan> plans =
        buildReconcilePlans(activeEnvelopes, signersByEnvelopeId, providerStatusesByExternalId);

    if (plans.isEmpty()) {
      return new EsignReconcileResponse(
          activeEnvelopes.size(), providerStatusesByExternalId.size(), 0, List.of());
    }

    ReconcileOutcome outcome = applyPlans(plans);

    return new EsignReconcileResponse(
        activeEnvelopes.size(),
        providerStatusesByExternalId.size(),
        outcome.reconciledEnvelopeIds().size(),
        outcome.reconciledEnvelopeIds());
  }

  private List<EsignEnvelopeEntity> loadActiveEnvelopes() {
    return envelopeRepository.findByStatusInAndExternalEnvelopeIdIsNotNull(
        List.of(
            EsignEnvelopeStatus.CREATED, EsignEnvelopeStatus.SENT, EsignEnvelopeStatus.DELIVERED));
  }

  private Map<Long, List<EsignSignerEntity>> loadSignersByEnvelopeId(
      List<EsignEnvelopeEntity> activeEnvelopes) {
    return signerRepository
        .findByEnvelopeIdIn(activeEnvelopes.stream().map(EsignEnvelopeEntity::getId).toList())
        .stream()
        .collect(
            Collectors.groupingBy(
                EsignSignerEntity::getEnvelopeId, LinkedHashMap::new, Collectors.toList()));
  }

  private Map<String, EnvelopeStatusResult> loadProviderStatusesByExternalId(
      List<EsignEnvelopeEntity> activeEnvelopes) {
    return listProviderStatusesInBatches(
            activeEnvelopes.stream().map(EsignEnvelopeEntity::getExternalEnvelopeId).toList())
        .stream()
        .filter(result -> StringUtils.isNotBlank(result.externalEnvelopeId()))
        .collect(
            Collectors.toMap(
                EnvelopeStatusResult::externalEnvelopeId,
                Function.identity(),
                (left, right) -> right,
                LinkedHashMap::new));
  }

  private List<ReconcilePlan> buildReconcilePlans(
      List<EsignEnvelopeEntity> activeEnvelopes,
      Map<Long, List<EsignSignerEntity>> signersByEnvelopeId,
      Map<String, EnvelopeStatusResult> providerStatusesByExternalId) {
    List<ReconcilePlan> plans = new ArrayList<>();
    for (EsignEnvelopeEntity envelope : activeEnvelopes) {
      EnvelopeStatusResult providerStatus =
          providerStatusesByExternalId.get(envelope.getExternalEnvelopeId());
      if (providerStatus == null) {
        continue;
      }
      ReconcilePlan plan =
          planReconcileUpdate(
              envelope,
              providerStatus,
              signersByEnvelopeId.getOrDefault(envelope.getId(), List.of()));
      if (plan != null) {
        plans.add(plan);
      }
    }
    return plans;
  }

  private ReconcileOutcome applyPlans(List<ReconcilePlan> plans) {
    ReconcileBatchResult batchResult =
        Objects.requireNonNull(
            transactionTemplate.execute(
                status -> {
                  List<EsignEnvelopeEntity> savedEnvelopes = new ArrayList<>();
                  envelopeRepository
                      .saveAll(plans.stream().map(ReconcilePlan::updatedEnvelope).toList())
                      .forEach(savedEnvelopes::add);

                  List<EsignSignerEntity> signerUpdates =
                      plans.stream().flatMap(plan -> plan.updatedSigners().stream()).toList();
                  if (!signerUpdates.isEmpty()) {
                    signerRepository.saveAll(signerUpdates);
                  }
                  return new ReconcileBatchResult(savedEnvelopes);
                }),
            "Reconciliation batch transaction returned no result");

    Map<Long, EsignEnvelopeEntity> savedEnvelopesById =
        batchResult.savedEnvelopes().stream()
            .collect(Collectors.toMap(EsignEnvelopeEntity::getId, Function.identity()));

    List<Long> reconciledEnvelopeIds = new ArrayList<>();
    for (ReconcilePlan plan : plans) {
      EsignEnvelopeEntity savedEnvelope = savedEnvelopesById.get(plan.updatedEnvelope().getId());
      envelopeSupport.recordEnvelopeUpdatedAudit(plan.existingEnvelope(), savedEnvelope);
      if (plan.needsCompletedArtifacts()) {
        envelopeSupport.ensureSignedDocumentStored(savedEnvelope);
        envelopeSupport.ensureCertificateStored(savedEnvelope);
      }
      reconciledEnvelopeIds.add(savedEnvelope.getId());
    }
    return new ReconcileOutcome(List.copyOf(reconciledEnvelopeIds));
  }

  private ReconcilePlan planReconcileUpdate(
      EsignEnvelopeEntity existing,
      EnvelopeStatusResult providerStatus,
      List<EsignSignerEntity> signers) {
    Instant providerTimestamp = firstNonNull(providerStatus.providerTimestamp(), Instant.now());
    String providerVoidedReason = StringUtils.trimToNull(providerStatus.voidedReason());
    boolean meaningfulEnvelopeChange =
        existing.getStatus() != providerStatus.envelopeStatus()
            || !Objects.equals(existing.getVoidedReason(), providerVoidedReason)
            || (providerStatus.envelopeStatus() == EsignEnvelopeStatus.COMPLETED
                && existing.getCompletedAt() == null);
    if (!meaningfulEnvelopeChange
        && providerStatus.envelopeStatus() != EsignEnvelopeStatus.VOIDED) {
      return null;
    }

    List<EsignSignerEntity> updatedSigners = List.of();
    if (providerStatus.envelopeStatus() == EsignEnvelopeStatus.VOIDED) {
      updatedSigners =
          signers.stream()
              .<EsignSignerEntity>map(
                  signer ->
                      signer.toBuilder()
                          .status(EsignSignerStatus.VOIDED)
                          .lastStatusAt(providerTimestamp)
                          .build())
              .toList();
    } else if (providerStatus.envelopeStatus() == EsignEnvelopeStatus.COMPLETED) {
      updatedSigners =
          signers.stream()
              .<EsignSignerEntity>map(
                  signer ->
                      signer.toBuilder()
                          .status(EsignSignerStatus.COMPLETED)
                          .completedAt(firstNonNull(signer.getCompletedAt(), providerTimestamp))
                          .lastStatusAt(providerTimestamp)
                          .build())
              .toList();
    }

    return new ReconcilePlan(
        existing,
        existing.toBuilder()
            .status(providerStatus.envelopeStatus())
            .voidedReason(
                providerStatus.envelopeStatus() == EsignEnvelopeStatus.VOIDED
                    ? providerVoidedReason
                    : existing.getVoidedReason())
            .completedAt(
                providerStatus.envelopeStatus() == EsignEnvelopeStatus.COMPLETED
                    ? firstNonNull(existing.getCompletedAt(), providerTimestamp)
                    : existing.getCompletedAt())
            .lastProviderUpdateAt(providerTimestamp)
            .build(),
        updatedSigners,
        providerStatus.envelopeStatus() == EsignEnvelopeStatus.COMPLETED);
  }

  private List<EnvelopeStatusResult> listProviderStatusesInBatches(
      List<String> externalEnvelopeIds) {
    if (externalEnvelopeIds == null || externalEnvelopeIds.isEmpty()) {
      return List.of();
    }
    List<EnvelopeStatusResult> results = new ArrayList<>();
    for (int start = 0;
        start < externalEnvelopeIds.size();
        start += RECONCILE_PROVIDER_BATCH_SIZE) {
      int end = Math.min(start + RECONCILE_PROVIDER_BATCH_SIZE, externalEnvelopeIds.size());
      results.addAll(docuSignGateway.listEnvelopeStatuses(externalEnvelopeIds.subList(start, end)));
    }
    return List.copyOf(results);
  }

  private static <T> T firstNonNull(T left, T right) {
    return left != null ? left : right;
  }

  private void enforceSystemScopeForReconcile() {
    if (currentUserProvider.currentTenantId().orElse(null) != null) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN,
          "Envelope reconciliation is only available to users without a tenant");
    }
  }

  private record ReconcilePlan(
      EsignEnvelopeEntity existingEnvelope,
      EsignEnvelopeEntity updatedEnvelope,
      List<EsignSignerEntity> updatedSigners,
      boolean needsCompletedArtifacts) {}

  private record ReconcileBatchResult(List<EsignEnvelopeEntity> savedEnvelopes) {}

  private record ReconcileOutcome(List<Long> reconciledEnvelopeIds) {}
}
