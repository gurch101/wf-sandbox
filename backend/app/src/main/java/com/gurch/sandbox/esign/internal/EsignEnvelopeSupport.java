package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.esign.internal.models.EsignEnvelopeEntity;
import com.gurch.sandbox.esign.internal.models.EsignSignerEntity;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StoredObjectApi;
import com.gurch.sandbox.storage.dto.StoreObjectRequest;
import com.gurch.sandbox.storage.dto.StoredObject;
import com.gurch.sandbox.web.NotFoundException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class EsignEnvelopeSupport {

  private static final String RESOURCE_TYPE = "esign-envelopes";
  private static final String SIGNED_NAMESPACE = "esign/signed";
  private static final String CERTIFICATE_NAMESPACE = "esign/certificates";

  private final EsignEnvelopeRepository envelopeRepository;
  private final EsignSignerRepository signerRepository;
  private final StoredObjectApi storedObjectApi;
  private final DocuSignGateway docuSignGateway;
  private final AuditLogApi auditLogApi;
  private final CurrentUserProvider currentUserProvider;
  private final TransactionTemplate transactionTemplate;

  EsignEnvelopeEntity loadAccessibleEnvelope(Long id) {
    EsignEnvelopeEntity entity = loadEnvelopeById(id);
    enforceTenantAccess(entity);
    return entity;
  }

  EsignEnvelopeEntity loadEnvelopeById(Long id) {
    return envelopeRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("E-sign envelope not found with id: " + id));
  }

  List<EsignSignerEntity> loadSigners(Long envelopeId) {
    return signerRepository.findByEnvelopeId(envelopeId).stream()
        .sorted(
            Comparator.comparing(EsignSignerEntity::getRoutingOrder)
                .thenComparing(EsignSignerEntity::getRoleKey))
        .toList();
  }

  void recordEnvelopeUpdatedAudit(
      EsignEnvelopeEntity existingEnvelope, EsignEnvelopeEntity updatedEnvelope) {
    auditLogApi.recordUpdate(
        RESOURCE_TYPE, updatedEnvelope.getId(), existingEnvelope, updatedEnvelope);
  }

  EsignEnvelopeEntity ensureSignedDocumentStored(EsignEnvelopeEntity existing) {
    if (existing.getSignedStorageObjectId() != null) {
      return existing;
    }
    StoredObject sourceDocument =
        requireStoredObject(existing.getSourceStorageObjectId(), existing.getId());
    byte[] sourceBytes = readStoredBytes(existing.getSourceStorageObjectId());
    DownloadedDocument signedDocument =
        docuSignGateway.downloadCompletedDocument(
            existing.getExternalEnvelopeId(), sourceDocument.fileName(), sourceBytes);
    StoredObject stored =
        writeToStorage(
            SIGNED_NAMESPACE,
            signedDocument.getFileName(),
            signedDocument.getMimeType(),
            signedDocument.getContent());
    return attachStoredArtifact(
        existing,
        stored,
        EsignEnvelopeEntity::getSignedStorageObjectId,
        (envelope, storageObjectId) ->
            envelope.toBuilder().signedStorageObjectId(storageObjectId).build(),
        "Signed-document storage update returned no envelope");
  }

  EsignEnvelopeEntity ensureCertificateStored(EsignEnvelopeEntity existing) {
    if (existing.getCertificateStorageObjectId() != null) {
      return existing;
    }
    List<EsignSignerEntity> signers = loadSigners(existing.getId());
    DownloadedCertificate certificate =
        docuSignGateway.downloadCertificate(
            existing.getExternalEnvelopeId(),
            existing.getSubject(),
            signers.stream()
                .map(
                    signer ->
                        new SignerSnapshot(
                            signer.getFullName(),
                            signer.getEmail(),
                            signer.getRoleKey(),
                            signer.getCompletedAt()))
                .toList());
    StoredObject stored =
        writeToStorage(
            CERTIFICATE_NAMESPACE,
            certificate.getFileName(),
            certificate.getMimeType(),
            certificate.getContent());
    return attachStoredArtifact(
        existing,
        stored,
        EsignEnvelopeEntity::getCertificateStorageObjectId,
        (envelope, storageObjectId) ->
            envelope.toBuilder().certificateStorageObjectId(storageObjectId).build(),
        "Certificate storage update returned no envelope");
  }

  private EsignEnvelopeEntity attachStoredArtifact(
      EsignEnvelopeEntity existing,
      StoredObject stored,
      Function<EsignEnvelopeEntity, Long> storageObjectIdExtractor,
      BiFunction<EsignEnvelopeEntity, Long, EsignEnvelopeEntity> storageObjectIdAssigner,
      String nullUpdateMessage) {
    EsignEnvelopeEntity updated =
        transactionTemplate.execute(
            status -> {
              EsignEnvelopeEntity reloaded = loadEnvelopeById(existing.getId());
              if (storageObjectIdExtractor.apply(reloaded) != null) {
                return reloaded;
              }
              return envelopeRepository.save(storageObjectIdAssigner.apply(reloaded, stored.id()));
            });
    if (updated == null) {
      throw new IllegalStateException(nullUpdateMessage);
    }
    if (!Objects.equals(storageObjectIdExtractor.apply(updated), stored.id())) {
      cleanupStorageQuietly(stored.id());
      return updated;
    }
    recordEnvelopeUpdatedAudit(existing, updated);
    return updated;
  }

  private void enforceTenantAccess(EsignEnvelopeEntity entity) {
    Integer currentTenantId = currentUserProvider.currentTenantId().orElse(null);
    if (!Objects.equals(currentTenantId, entity.getTenantId())) {
      throw new NotFoundException("E-sign envelope not found with id: " + entity.getId());
    }
  }

  private StoredObject writeToStorage(
      String namespace, String filename, String mimeType, byte[] bytes) {
    return storedObjectApi.store(
        StoreObjectRequest.builder()
            .namespace(namespace)
            .fileName(filename)
            .mimeType(mimeType)
            .contentStream(new ByteArrayInputStream(bytes))
            .build());
  }

  private byte[] readStoredBytes(Long storageObjectId) {
    try (InputStream inputStream = storedObjectApi.read(storageObjectId)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored e-sign artifact", e);
    }
  }

  private void cleanupStorageQuietly(Long storageObjectId) {
    if (storageObjectId == null) {
      return;
    }
    try {
      storedObjectApi.delete(storageObjectId);
    } catch (IOException ignored) {
      // Best-effort storage cleanup only.
    }
  }

  private StoredObject requireStoredObject(Long storageObjectId, Long envelopeId) {
    return storedObjectApi
        .findById(storageObjectId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Stored object is missing for e-sign envelope id: " + envelopeId));
  }
}
