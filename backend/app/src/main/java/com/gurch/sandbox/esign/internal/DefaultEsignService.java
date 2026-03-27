package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.esign.EsignApi;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignCertificateDownload;
import com.gurch.sandbox.esign.EsignCreateEnvelopeCommand;
import com.gurch.sandbox.esign.EsignCreateEnvelopeRequest;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEmbeddedViewResponse;
import com.gurch.sandbox.esign.EsignEnvelopeResponse;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignErrorCode;
import com.gurch.sandbox.esign.EsignSignerResponse;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignSignedDocumentDownload;
import com.gurch.sandbox.esign.EsignWebhookRequest;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StorageApi;
import com.gurch.sandbox.storage.StorageWriteRequest;
import com.gurch.sandbox.storage.StorageWriteResult;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultEsignService implements EsignApi {

  private static final String RESOURCE_TYPE = "esign-envelopes";
  private static final String SOURCE_NAMESPACE = "esign/envelopes";
  private static final String SIGNED_NAMESPACE = "esign/signed";
  private static final String CERTIFICATE_NAMESPACE = "esign/certificates";
  private static final String PDF_MIME_TYPE = "application/pdf";

  private final EsignEnvelopeRepository envelopeRepository;
  private final EsignSignerRepository signerRepository;
  private final StorageApi storageApi;
  private final PdfAnchorParserService pdfAnchorParserService;
  private final DocuSignGateway docuSignGateway;
  private final AuditLogApi auditLogApi;
  private final CurrentUserProvider currentUserProvider;

  @Override
  @Transactional
  public EsignEnvelopeResponse createEnvelope(EsignCreateEnvelopeCommand command) {
    validateCreateCommand(command);

    byte[] payload = readAllBytes(command.getContentStream());
    PdfAnchorParseResult anchorParseResult = pdfAnchorParserService.parse(payload);
    validateAnchors(command.getSigners(), anchorParseResult);

    StorageWriteResult stored =
        writeToStorage(SOURCE_NAMESPACE, command.getOriginalFilename(), payload);

    List<EsignSignerEntity> signerEntities = buildSignerEntities(command, anchorParseResult);
    DocuSignGateway.CreateEnvelopeResult providerEnvelope =
        docuSignGateway.createEnvelope(
            new DocuSignGateway.CreateEnvelopeRequest(
                command.getSubject(),
                StringUtils.trimToNull(command.getMessage()),
                command.getDeliveryMode(),
                command.getOriginalFilename(),
                payload,
                signerEntities.stream()
                    .map(
                        signer ->
                            new DocuSignGateway.SignerRequest(
                                signer.getRoleKey(),
                                signer.getFullName(),
                                signer.getEmail(),
                                signer.getPhoneNumber(),
                                signer.getAuthMethod(),
                                signerForRole(command.getSigners(), signer.getRoleKey()).passcode(),
                                signerForRole(command.getSigners(), signer.getRoleKey()).smsNumber(),
                                signer.getSignatureAnchorText(),
                                signer.getDateAnchorText(),
                                signer.getRoutingOrder()))
                    .toList(),
                command.isRemindersEnabled(),
                command.getReminderIntervalHours()));

    EsignEnvelopeEntity entity =
        EsignEnvelopeEntity.builder()
            .externalEnvelopeId(providerEnvelope.externalEnvelopeId())
            .provider("DOCUSIGN")
            .subject(command.getSubject().trim())
            .message(StringUtils.trimToNull(command.getMessage()))
            .deliveryMode(command.getDeliveryMode())
            .status(providerEnvelope.envelopeStatus())
            .tenantId(currentUserProvider.currentTenantId().orElse(null))
            .sourceFileName(command.getOriginalFilename().trim())
            .sourceMimeType(PDF_MIME_TYPE)
            .sourceContentSize(stored.contentSize())
            .sourceChecksumSha256(stored.checksumSha256())
            .sourceStorageProvider(stored.provider())
            .sourceStoragePath(stored.storagePath())
            .remindersEnabled(command.isRemindersEnabled())
            .reminderIntervalHours(command.getReminderIntervalHours())
            .lastProviderUpdateAt(providerEnvelope.providerTimestamp())
            .build();

    try {
      EsignEnvelopeEntity savedEnvelope = envelopeRepository.save(entity);
      Map<String, DocuSignGateway.SignerResult> providerSignersByRole =
          providerEnvelope.signers().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      DocuSignGateway.SignerResult::roleKey,
                      java.util.function.Function.identity(),
                      (left, right) -> left,
                      LinkedHashMap::new));

      List<EsignSignerEntity> persistedSigners = new ArrayList<>();
      for (EsignSignerEntity signer : signerEntities) {
        DocuSignGateway.SignerResult providerSigner = providerSignersByRole.get(signer.getRoleKey());
        EsignSignerEntity persisted =
            signerRepository.save(
                signer.toBuilder()
                    .envelopeId(savedEnvelope.getId())
                    .providerRecipientId(providerSigner == null ? null : providerSigner.providerRecipientId())
                    .status(providerSigner == null ? signer.getStatus() : providerSigner.status())
                    .lastStatusAt(providerEnvelope.providerTimestamp())
                    .build());
        persistedSigners.add(persisted);
      }
      auditLogApi.recordCreate(RESOURCE_TYPE, savedEnvelope.getId(), savedEnvelope);
      return toResponse(savedEnvelope, persistedSigners);
    } catch (RuntimeException e) {
      cleanupStorageQuietly(stored.storagePath());
      throw e;
    }
  }

  @Override
  public Optional<EsignEnvelopeResponse> findById(Long id) {
    return Optional.of(loadAccessibleEnvelope(id)).map(envelope -> toResponse(envelope, loadSigners(envelope.getId())));
  }

  @Override
  @Transactional
  public EsignEnvelopeResponse voidEnvelope(Long id, String reason) {
    if (StringUtils.isBlank(reason)) {
      throw ValidationErrorException.of(EsignErrorCode.VOID_REASON_REQUIRED);
    }
    EsignEnvelopeEntity existing = loadAccessibleEnvelope(id);
    if (existing.getStatus() == EsignEnvelopeStatus.VOIDED) {
      throw ValidationErrorException.of(EsignErrorCode.ENVELOPE_ALREADY_VOIDED);
    }
    List<EsignSignerEntity> existingSigners = loadSigners(existing.getId());
    docuSignGateway.voidEnvelope(existing.getExternalEnvelopeId(), reason.trim());

    EsignEnvelopeEntity updated =
        envelopeRepository.save(
            existing.toBuilder()
                .status(EsignEnvelopeStatus.VOIDED)
                .voidedReason(reason.trim())
                .lastProviderUpdateAt(Instant.now())
                .build());
    List<EsignSignerEntity> updatedSigners =
        existingSigners.stream()
            .map(
                signer ->
                    signerRepository.save(
                        signer.toBuilder()
                            .status(EsignSignerStatus.VOIDED)
                            .lastStatusAt(Instant.now())
                            .build()))
            .toList();
    auditLogApi.recordUpdate(RESOURCE_TYPE, updated.getId(), existing, updated);
    return toResponse(updated, updatedSigners);
  }

  @Override
  @Transactional
  public void deleteEnvelope(Long id) {
    EsignEnvelopeEntity existing = loadAccessibleEnvelope(id);
    List<EsignSignerEntity> signers = loadSigners(existing.getId());
    docuSignGateway.deleteEnvelope(existing.getExternalEnvelopeId());
    signerRepository.deleteAll(signers);
    envelopeRepository.delete(existing);
    cleanupStorageQuietly(existing.getSourceStoragePath());
    cleanupStorageQuietly(existing.getSignedStoragePath());
    cleanupStorageQuietly(existing.getCertificateStoragePath());
    auditLogApi.recordDelete(RESOURCE_TYPE, existing.getId(), existing);
  }

  @Override
  public EsignEmbeddedViewResponse createEmbeddedSigningView(Long id, String roleKey) {
    EsignEnvelopeEntity envelope = loadAccessibleEnvelope(id);
    if (envelope.getDeliveryMode() != EsignDeliveryMode.IN_PERSON) {
      throw ValidationErrorException.of(EsignErrorCode.EMBEDDED_VIEW_IN_PERSON_ONLY);
    }
    String normalizedRoleKey = normalizeAnchorKey(roleKey);
    EsignSignerEntity signer =
        loadSigners(envelope.getId()).stream()
            .filter(candidate -> candidate.getRoleKey().equals(normalizedRoleKey))
            .findFirst()
            .orElseThrow(() -> new NotFoundException("E-sign signer not found with roleKey: " + roleKey));

    String signingUrl =
        docuSignGateway.createRecipientView(
            envelope.getExternalEnvelopeId(),
            new DocuSignGateway.SignerRequest(
                signer.getRoleKey(),
                signer.getFullName(),
                signer.getEmail(),
                signer.getPhoneNumber(),
                signer.getAuthMethod(),
                null,
                signer.getSmsNumber(),
                signer.getSignatureAnchorText(),
                signer.getDateAnchorText(),
                signer.getRoutingOrder()));
    return EsignEmbeddedViewResponse.builder()
        .roleKey(signer.getRoleKey())
        .signingUrl(signingUrl)
        .build();
  }

  @Override
  @Transactional
  public EsignCertificateDownload downloadCertificate(Long id) {
    EsignEnvelopeEntity existing = loadAccessibleEnvelope(id);
    if (existing.getStatus() != EsignEnvelopeStatus.COMPLETED) {
      throw ValidationErrorException.of(EsignErrorCode.CERTIFICATE_NOT_READY);
    }
    if (StringUtils.isNotBlank(existing.getCertificateStoragePath())) {
      return readCertificate(existing);
    }

    List<EsignSignerEntity> signers = loadSigners(existing.getId());
    DocuSignGateway.DownloadedCertificate certificate =
        docuSignGateway.downloadCertificate(
            existing.getExternalEnvelopeId(),
            existing.getSubject(),
            signers.stream()
                .map(
                    signer ->
                        new DocuSignGateway.SignerSnapshot(
                            signer.getFullName(),
                            signer.getEmail(),
                            signer.getRoleKey(),
                            signer.getCompletedAt()))
                .toList());
    StorageWriteResult stored =
        writeToStorage(CERTIFICATE_NAMESPACE, certificate.getFileName(), certificate.getContent());

    EsignEnvelopeEntity updated =
        envelopeRepository.save(
            existing.toBuilder()
                .certificateFileName(certificate.getFileName())
                .certificateMimeType(certificate.getMimeType())
                .certificateContentSize(stored.contentSize())
                .certificateChecksumSha256(stored.checksumSha256())
                .certificateStorageProvider(stored.provider())
                .certificateStoragePath(stored.storagePath())
                .build());
    auditLogApi.recordUpdate(RESOURCE_TYPE, updated.getId(), existing, updated);
    return readCertificate(updated);
  }

  @Override
  @Transactional
  public EsignSignedDocumentDownload downloadSignedDocument(Long id) {
    EsignEnvelopeEntity existing = loadAccessibleEnvelope(id);
    if (existing.getStatus() != EsignEnvelopeStatus.COMPLETED) {
      throw ValidationErrorException.of(EsignErrorCode.SIGNED_DOCUMENT_NOT_READY);
    }
    EsignEnvelopeEntity updated = ensureSignedDocumentStored(existing);
    return readSignedDocument(updated);
  }

  @Override
  @Transactional
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
    enforceTenantAccess(existing);

    Instant eventTimestamp = request.eventTimestamp() != null ? request.eventTimestamp() : Instant.now();
    EsignEnvelopeStatus newStatus =
        request.envelopeStatus() == null ? existing.getStatus() : request.envelopeStatus();
    EsignEnvelopeEntity updatedEnvelope =
        envelopeRepository.save(
            existing.toBuilder()
                .status(newStatus)
                .completedAt(newStatus == EsignEnvelopeStatus.COMPLETED ? firstNonNull(existing.getCompletedAt(), eventTimestamp) : existing.getCompletedAt())
                .lastProviderUpdateAt(eventTimestamp)
                .build());
    if (newStatus == EsignEnvelopeStatus.COMPLETED) {
      updatedEnvelope = ensureSignedDocumentStored(updatedEnvelope);
    }

    Map<String, EsignWebhookRequest.SignerStatusUpdate> updatesByRole =
        new LinkedHashMap<>();
    if (request.signers() != null) {
      for (EsignWebhookRequest.SignerStatusUpdate signer : request.signers()) {
        if (signer != null && StringUtils.isNotBlank(signer.roleKey())) {
          updatesByRole.put(signer.roleKey().trim().toLowerCase(Locale.ROOT), signer);
        }
      }
    }

    for (EsignSignerEntity signer : loadSigners(existing.getId())) {
      EsignWebhookRequest.SignerStatusUpdate update = updatesByRole.get(signer.getRoleKey());
      if (update == null) {
        continue;
      }
      signerRepository.save(
          signer.toBuilder()
              .providerRecipientId(
                  StringUtils.defaultIfBlank(update.providerRecipientId(), signer.getProviderRecipientId()))
              .status(update.status() == null ? signer.getStatus() : update.status())
              .viewedAt(update.viewedAt() == null ? signer.getViewedAt() : update.viewedAt())
              .completedAt(update.completedAt() == null ? signer.getCompletedAt() : update.completedAt())
              .lastStatusAt(eventTimestamp)
              .build());
    }
    auditLogApi.recordUpdate(RESOURCE_TYPE, updatedEnvelope.getId(), existing, updatedEnvelope);
  }

  private EsignCertificateDownload readCertificate(EsignEnvelopeEntity envelope) {
    try {
      return EsignCertificateDownload.builder()
          .fileName(envelope.getCertificateFileName())
          .mimeType(envelope.getCertificateMimeType())
          .contentSize(envelope.getCertificateContentSize())
          .contentStream(storageApi.read(envelope.getCertificateStoragePath()))
          .build();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored signing certificate", e);
    }
  }

  private EsignSignedDocumentDownload readSignedDocument(EsignEnvelopeEntity envelope) {
    try {
      return EsignSignedDocumentDownload.builder()
          .fileName(envelope.getSignedFileName())
          .mimeType(envelope.getSignedMimeType())
          .contentSize(envelope.getSignedContentSize())
          .contentStream(storageApi.read(envelope.getSignedStoragePath()))
          .build();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored signed document", e);
    }
  }

  private EsignEnvelopeEntity ensureSignedDocumentStored(EsignEnvelopeEntity existing) {
    if (StringUtils.isNotBlank(existing.getSignedStoragePath())) {
      return existing;
    }
    byte[] sourceBytes = readStoredBytes(existing.getSourceStoragePath());
    DocuSignGateway.DownloadedDocument signedDocument =
        docuSignGateway.downloadCompletedDocument(
            existing.getExternalEnvelopeId(), existing.getSourceFileName(), sourceBytes);
    StorageWriteResult stored =
        writeToStorage(SIGNED_NAMESPACE, signedDocument.getFileName(), signedDocument.getContent());

    EsignEnvelopeEntity updated =
        envelopeRepository.save(
            existing.toBuilder()
                .signedFileName(signedDocument.getFileName())
                .signedMimeType(signedDocument.getMimeType())
                .signedContentSize(stored.contentSize())
                .signedChecksumSha256(stored.checksumSha256())
                .signedStorageProvider(stored.provider())
                .signedStoragePath(stored.storagePath())
                .build());
    auditLogApi.recordUpdate(RESOURCE_TYPE, updated.getId(), existing, updated);
    return updated;
  }

  private EsignEnvelopeEntity loadAccessibleEnvelope(Long id) {
    EsignEnvelopeEntity entity =
        envelopeRepository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("E-sign envelope not found with id: " + id));
    enforceTenantAccess(entity);
    return entity;
  }

  private void enforceTenantAccess(EsignEnvelopeEntity entity) {
    Integer currentTenantId = currentUserProvider.currentTenantId().orElse(null);
    if (currentTenantId != null && entity.getTenantId() != null && !currentTenantId.equals(entity.getTenantId())) {
      throw new NotFoundException("E-sign envelope not found with id: " + entity.getId());
    }
  }

  private List<EsignSignerEntity> loadSigners(Long envelopeId) {
    return signerRepository.findByEnvelopeId(envelopeId).stream()
        .sorted(Comparator.comparing(EsignSignerEntity::getRoutingOrder).thenComparing(EsignSignerEntity::getRoleKey))
        .toList();
  }

  private EsignEnvelopeResponse toResponse(EsignEnvelopeEntity envelope, List<EsignSignerEntity> signers) {
    return EsignEnvelopeResponse.builder()
        .id(envelope.getId())
        .externalEnvelopeId(envelope.getExternalEnvelopeId())
        .subject(envelope.getSubject())
        .message(envelope.getMessage())
        .fileName(envelope.getSourceFileName())
        .mimeType(envelope.getSourceMimeType())
        .deliveryMode(envelope.getDeliveryMode())
        .status(envelope.getStatus())
        .tenantId(envelope.getTenantId())
        .remindersEnabled(envelope.isRemindersEnabled())
        .reminderIntervalHours(envelope.getReminderIntervalHours())
        .voidedReason(envelope.getVoidedReason())
        .completedAt(envelope.getCompletedAt())
        .lastProviderUpdateAt(envelope.getLastProviderUpdateAt())
        .certificateReady(
            StringUtils.isNotBlank(envelope.getCertificateStoragePath())
                || envelope.getStatus() == EsignEnvelopeStatus.COMPLETED)
        .signedDocumentReady(
            StringUtils.isNotBlank(envelope.getSignedStoragePath())
                || envelope.getStatus() == EsignEnvelopeStatus.COMPLETED)
        .signers(signers.stream().map(this::toSignerResponse).toList())
        .createdAt(envelope.getCreatedAt())
        .updatedAt(envelope.getUpdatedAt())
        .version(envelope.getVersion())
        .build();
  }

  private EsignSignerResponse toSignerResponse(EsignSignerEntity signer) {
    return EsignSignerResponse.builder()
        .roleKey(signer.getRoleKey())
        .signatureAnchorText(signer.getSignatureAnchorText())
        .dateAnchorText(signer.getDateAnchorText())
        .routingOrder(signer.getRoutingOrder())
        .fullName(signer.getFullName())
        .email(signer.getEmail())
        .phoneNumber(signer.getPhoneNumber())
        .authMethod(signer.getAuthMethod())
        .providerRecipientId(signer.getProviderRecipientId())
        .status(signer.getStatus())
        .viewedAt(signer.getViewedAt())
        .completedAt(signer.getCompletedAt())
        .lastStatusAt(signer.getLastStatusAt())
        .build();
  }

  private void validateCreateCommand(EsignCreateEnvelopeCommand command) {
    if (command == null || command.getContentStream() == null) {
      throw ValidationErrorException.of(EsignErrorCode.FILE_REQUIRED);
    }
    if (StringUtils.isBlank(command.getOriginalFilename())) {
      throw ValidationErrorException.of(EsignErrorCode.FILE_REQUIRED);
    }
    if (!PDF_MIME_TYPE.equals(StringUtils.trimToEmpty(command.getMimeType()))) {
      throw ValidationErrorException.of(EsignErrorCode.UNSUPPORTED_FILE_TYPE);
    }
    if (StringUtils.isBlank(command.getSubject())) {
      throw ValidationErrorException.of(EsignErrorCode.SUBJECT_REQUIRED);
    }
    if (command.getSigners() == null || command.getSigners().isEmpty()) {
      throw ValidationErrorException.of(EsignErrorCode.SIGNERS_REQUIRED);
    }
    if (command.getDeliveryMode() == EsignDeliveryMode.REMOTE) {
      if (command.isRemindersEnabled() && command.getReminderIntervalHours() == null) {
        throw ValidationErrorException.of(EsignErrorCode.REMINDER_INTERVAL_REQUIRED);
      }
    } else if (command.isRemindersEnabled()) {
      throw ValidationErrorException.of(EsignErrorCode.REMINDERS_REMOTE_ONLY);
    }
  }

  private void validateAnchors(
      List<EsignCreateEnvelopeRequest.SignerInput> signers, PdfAnchorParseResult anchorParseResult) {
    LinkedHashSet<String> seenAnchorKeys = new LinkedHashSet<>();
    for (EsignCreateEnvelopeRequest.SignerInput signer : signers) {
      String anchorKey = normalizeAnchorKey(signer.anchorKey());
      if (anchorKey == null) {
        throw ValidationErrorException.of(EsignErrorCode.INVALID_ANCHOR_KEY);
      }
      if (!seenAnchorKeys.add(anchorKey)) {
        throw ValidationErrorException.of(EsignErrorCode.DUPLICATE_ANCHOR_KEY);
      }
      if (!anchorParseResult.signatureAnchorKeys().contains(anchorKey)) {
        throw ValidationErrorException.of(EsignErrorCode.MISSING_SIGNATURE_ANCHOR);
      }
    }
  }

  private List<EsignSignerEntity> buildSignerEntities(
      EsignCreateEnvelopeCommand command, PdfAnchorParseResult anchorParseResult) {
    List<EsignSignerEntity> entities = new ArrayList<>();
    int defaultRoutingOrder = 1;
    for (EsignCreateEnvelopeRequest.SignerInput signer : command.getSigners()) {
      String anchorKey = normalizeAnchorKey(signer.anchorKey());
      validateSigner(command.getDeliveryMode(), signer);
      String dateAnchorKey = "d" + anchorKey.substring(1);
      entities.add(
          EsignSignerEntity.builder()
              .roleKey(anchorKey)
              .signatureAnchorText("/" + anchorKey + "/")
              .dateAnchorText(anchorParseResult.dateAnchorKeys().contains(dateAnchorKey) ? "/" + dateAnchorKey + "/" : null)
              .routingOrder(signer.routingOrder() == null ? defaultRoutingOrder : signer.routingOrder())
              .fullName(signer.fullName().trim())
              .email(signer.email().trim())
              .phoneNumber(StringUtils.trimToNull(signer.phoneNumber()))
              .authMethod(signer.authMethod())
              .smsNumber(StringUtils.trimToNull(signer.smsNumber()))
              .status(EsignSignerStatus.SENT)
              .build());
      defaultRoutingOrder++;
    }
    return entities.stream()
        .sorted(Comparator.comparing(EsignSignerEntity::getRoutingOrder).thenComparing(EsignSignerEntity::getRoleKey))
        .toList();
  }

  private EsignCreateEnvelopeRequest.SignerInput signerForRole(
      List<EsignCreateEnvelopeRequest.SignerInput> signers, String roleKey) {
    return signers.stream()
        .filter(signer -> roleKey.equals(normalizeAnchorKey(signer.anchorKey())))
        .findFirst()
        .orElseThrow();
  }

  private void validateSigner(EsignDeliveryMode deliveryMode, EsignCreateEnvelopeRequest.SignerInput signer) {
    if (deliveryMode == EsignDeliveryMode.REMOTE) {
      if (signer.authMethod() == null || signer.authMethod() == EsignAuthMethod.NONE) {
        throw ValidationErrorException.of(EsignErrorCode.REMOTE_AUTH_REQUIRED);
      }
      if (signer.authMethod() == EsignAuthMethod.PASSCODE && StringUtils.isBlank(signer.passcode())) {
        throw ValidationErrorException.of(EsignErrorCode.PASSCODE_REQUIRED);
      }
      if (signer.authMethod() == EsignAuthMethod.SMS && StringUtils.isBlank(signer.smsNumber())) {
        throw ValidationErrorException.of(EsignErrorCode.SMS_NUMBER_REQUIRED);
      }
      return;
    }
    if (signer.authMethod() != EsignAuthMethod.NONE) {
      throw ValidationErrorException.of(EsignErrorCode.IN_PERSON_AUTH_NOT_ALLOWED);
    }
  }

  private static String normalizeAnchorKey(String rawAnchorKey) {
    String normalized = StringUtils.trimToNull(rawAnchorKey);
    if (normalized == null) {
      return null;
    }
    normalized = normalized.toLowerCase(Locale.ROOT);
    return normalized.matches("s\\d+") ? normalized : null;
  }

  private byte[] readAllBytes(InputStream inputStream) {
    try (InputStream source = inputStream) {
      return source.readAllBytes();
    } catch (IOException e) {
      throw ValidationErrorException.of(EsignErrorCode.FILE_READ_FAILED);
    }
  }

  private StorageWriteResult writeToStorage(String namespace, String filename, byte[] bytes) {
    return writeToStorage(namespace, filename, new ByteArrayInputStream(bytes));
  }

  private StorageWriteResult writeToStorage(String namespace, String filename, InputStream contentStream) {
    try {
      return storageApi.write(
          StorageWriteRequest.builder()
              .namespace(namespace)
              .originalFilename(filename)
              .contentStream(contentStream)
              .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist e-sign artifact", e);
    }
  }

  private byte[] readStoredBytes(String storagePath) {
    try (InputStream inputStream = storageApi.read(storagePath)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored e-sign artifact", e);
    }
  }

  private void cleanupStorageQuietly(String storagePath) {
    if (StringUtils.isBlank(storagePath)) {
      return;
    }
    try {
      storageApi.delete(storagePath);
    } catch (IOException ignored) {
      // Best-effort storage cleanup only.
    }
  }

  private static <T> T firstNonNull(T left, T right) {
    return left != null ? left : right;
  }
}
