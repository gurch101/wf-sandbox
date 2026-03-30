package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignWebhookApi;
import com.gurch.sandbox.esign.dto.EsignWebhookRequest;
import com.gurch.sandbox.esign.internal.models.EsignEnvelopeEntity;
import com.gurch.sandbox.esign.internal.models.EsignSignerEntity;
import com.gurch.sandbox.web.NotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DefaultEsignWebhookService implements EsignWebhookApi {

  private final EsignEnvelopeRepository envelopeRepository;
  private final EsignSignerRepository signerRepository;
  private final EsignEnvelopeSupport envelopeSupport;
  private final TransactionTemplate transactionTemplate;

  @Override
  public void handleWebhook(EsignWebhookRequest request) {
    if (request == null || StringUtils.isBlank(request.externalEnvelopeId())) {
      return;
    }
    EsignEnvelopeEntity existing =
        envelopeRepository
            .findByExternalEnvelopeId(request.externalEnvelopeId().trim())
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "E-sign envelope not found with external id: "
                            + request.externalEnvelopeId().trim()));

    Instant eventTimestamp =
        request.eventTimestamp() != null ? request.eventTimestamp() : Instant.now();

    Map<String, EsignWebhookRequest.SignerStatusUpdate> updatesByRecipientId =
        new LinkedHashMap<>();
    if (request.signers() != null) {
      for (EsignWebhookRequest.SignerStatusUpdate signer : request.signers()) {
        if (StringUtils.isNotBlank(signer.providerRecipientId())) {
          updatesByRecipientId.put(signer.providerRecipientId().trim(), signer);
        }
      }
    }
    EsignEnvelopeStatus newStatus =
        inferEnvelopeStatus(existing.getStatus(), request.envelopeStatus(), request.signers());

    EsignEnvelopeEntity updatedEnvelope =
        transactionTemplate.execute(
            status -> {
              EsignEnvelopeEntity updated =
                  envelopeRepository.save(
                      existing.toBuilder()
                          .status(newStatus)
                          .completedAt(
                              newStatus == EsignEnvelopeStatus.COMPLETED
                                  ? firstNonNull(existing.getCompletedAt(), eventTimestamp)
                                  : existing.getCompletedAt())
                          .lastProviderUpdateAt(eventTimestamp)
                          .build());

              for (EsignSignerEntity signer : envelopeSupport.loadSigners(existing.getId())) {
                if (StringUtils.isBlank(signer.getProviderRecipientId())) {
                  continue;
                }
                EsignWebhookRequest.SignerStatusUpdate update =
                    updatesByRecipientId.get(signer.getProviderRecipientId());
                if (update == null) {
                  continue;
                }
                signerRepository.save(
                    signer.toBuilder()
                        .providerRecipientId(
                            StringUtils.defaultIfBlank(
                                update.providerRecipientId(), signer.getProviderRecipientId()))
                        .status(update.status() == null ? signer.getStatus() : update.status())
                        .viewedAt(
                            update.viewedAt() == null ? signer.getViewedAt() : update.viewedAt())
                        .completedAt(
                            update.completedAt() == null
                                ? signer.getCompletedAt()
                                : update.completedAt())
                        .lastStatusAt(eventTimestamp)
                        .build());
              }
              envelopeSupport.recordEnvelopeUpdatedAudit(existing, updated);
              return updated;
            });

    if (newStatus == EsignEnvelopeStatus.COMPLETED) {
      envelopeSupport.ensureSignedDocumentStored(updatedEnvelope);
      envelopeSupport.ensureCertificateStored(updatedEnvelope);
    }
  }

  private static <T> T firstNonNull(T left, T right) {
    return left != null ? left : right;
  }

  private EsignEnvelopeStatus inferEnvelopeStatus(
      EsignEnvelopeStatus existingStatus,
      EsignEnvelopeStatus requestedEnvelopeStatus,
      java.util.List<EsignWebhookRequest.SignerStatusUpdate> signerUpdates) {
    if (requestedEnvelopeStatus != null) {
      return requestedEnvelopeStatus;
    }
    boolean hasDeclinedSigner =
        signerUpdates != null
            && signerUpdates.stream()
                .anyMatch(update -> update.status() == EsignSignerStatus.DECLINED);
    return hasDeclinedSigner ? EsignEnvelopeStatus.DECLINED : existingStatus;
  }
}
