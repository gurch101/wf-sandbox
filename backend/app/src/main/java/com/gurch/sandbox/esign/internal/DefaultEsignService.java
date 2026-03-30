package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.esign.EsignApi;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignCreateEnvelopeCommand;
import com.gurch.sandbox.esign.EsignCreateEnvelopeRequest;
import com.gurch.sandbox.esign.EsignCreateErrorCode;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignDocumentDownload;
import com.gurch.sandbox.esign.EsignDownloadErrorCode;
import com.gurch.sandbox.esign.EsignEmbeddedViewErrorCode;
import com.gurch.sandbox.esign.EsignEmbeddedViewResponse;
import com.gurch.sandbox.esign.EsignEnvelopeResponse;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignResendErrorCode;
import com.gurch.sandbox.esign.EsignSignerDeliveryMethod;
import com.gurch.sandbox.esign.EsignSignerResponse;
import com.gurch.sandbox.esign.EsignSignerStatus;
import com.gurch.sandbox.esign.EsignVoidErrorCode;
import com.gurch.sandbox.pdfutils.PdfAnchorParseResult;
import com.gurch.sandbox.pdfutils.PdfAnchorParser;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StoreObjectRequest;
import com.gurch.sandbox.storage.StoredObject;
import com.gurch.sandbox.storage.StoredObjectApi;
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
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class DefaultEsignService implements EsignApi {

  private static final String RESOURCE_TYPE = "esign-envelopes";
  private static final String SOURCE_NAMESPACE = "esign/envelopes";
  private static final String DEFAULT_SMS_COUNTRY_CODE = "1";

  private final EsignEnvelopeRepository envelopeRepository;
  private final EsignSignerRepository signerRepository;
  private final StoredObjectApi storedObjectApi;
  private final DocuSignGateway docuSignGateway;
  private final AuditLogApi auditLogApi;
  private final CurrentUserProvider currentUserProvider;
  private final TransactionTemplate transactionTemplate;
  private final EsignEnvelopeSupport envelopeSupport;

  @Override
  public EsignEnvelopeResponse createEnvelope(EsignCreateEnvelopeCommand command) {
    validateCreateCommand(command);

    byte[] payload = readAllBytes(command.getContentStream());
    PdfAnchorParseResult anchorParseResult = PdfAnchorParser.parse(payload);
    validateAnchors(command.getSigners(), anchorParseResult);

    StoredObject stored =
        writeToStorage(
            SOURCE_NAMESPACE,
            command.getOriginalFilename(),
            MediaType.APPLICATION_PDF_VALUE,
            payload);

    List<EsignSignerEntity> signerEntities = buildSignerEntities(command, anchorParseResult);
    PersistedPendingEnvelope pendingEnvelope;
    try {
      pendingEnvelope =
          Objects.requireNonNull(
              transactionTemplate.execute(
                  status -> persistPendingEnvelope(command, stored, signerEntities)),
              "Pending envelope transaction returned no result");
    } catch (RuntimeException e) {
      cleanupStorageQuietly(stored.id());
      throw e;
    }

    CreateEnvelopeResult providerEnvelope;
    try {
      providerEnvelope =
          docuSignGateway.createEnvelope(
              new CreateEnvelopeRequest(
                  command.getSubject(),
                  StringUtils.trimToNull(command.getMessage()),
                  command.getDeliveryMode(),
                  command.getOriginalFilename(),
                  payload,
                  signerEntities.stream()
                      .map(
                          signer ->
                              new SignerRequest(
                                  signer.getRoleKey(),
                                  signer.getFullName(),
                                  signer.getEmail(),
                                  signer.getProviderRecipientId(),
                                  command.getDeliveryMode(),
                                  signer.getDeliveryMethod(),
                                  signer.getAuthMethod(),
                                  signerForRole(command.getSigners(), signer.getRoleKey())
                                      .passcode(),
                                  signerForRole(command.getSigners(), signer.getRoleKey())
                                      .smsNumber(),
                                  signer.getSignatureAnchorText(),
                                  signer.getDateAnchorText(),
                                  signer.getRoutingOrder()))
                      .toList(),
                  command.isRemindersEnabled(),
                  command.getReminderIntervalHours()));
    } catch (RuntimeException e) {
      cleanupPendingEnvelope(pendingEnvelope.envelopeId(), stored.id());
      throw e;
    }

    try {
      return Objects.requireNonNull(
          transactionTemplate.execute(
              status -> finalizePendingEnvelope(pendingEnvelope.envelopeId(), providerEnvelope)),
          "Envelope finalization transaction returned no result");
    } catch (RuntimeException e) {
      boolean providerCleanupSucceeded =
          tryVoidEnvelopeQuietly(
              providerEnvelope.externalEnvelopeId(),
              "Local finalization failed after provider envelope creation");
      throw new IllegalStateException(
          "DocuSign envelope was created but local finalization failed for envelope id: "
              + pendingEnvelope.envelopeId()
              + ", externalEnvelopeId: "
              + providerEnvelope.externalEnvelopeId()
              + ", providerCleanupSucceeded: "
              + providerCleanupSucceeded,
          e);
    }
  }

  @Override
  public Optional<EsignEnvelopeResponse> findById(Long id) {
    return Optional.of(envelopeSupport.loadAccessibleEnvelope(id))
        .map(envelope -> toResponse(envelope, envelopeSupport.loadSigners(envelope.getId())));
  }

  @Override
  public EsignEnvelopeResponse voidEnvelope(Long id, String reason) {
    if (StringUtils.isBlank(reason)) {
      throw ValidationErrorException.of(EsignVoidErrorCode.VOID_REASON_REQUIRED);
    }
    EsignEnvelopeEntity existing = envelopeSupport.loadAccessibleEnvelope(id);
    if (existing.getStatus() == EsignEnvelopeStatus.VOIDED) {
      throw ValidationErrorException.of(EsignVoidErrorCode.ENVELOPE_ALREADY_VOIDED);
    }
    List<EsignSignerEntity> existingSigners = envelopeSupport.loadSigners(existing.getId());
    String trimmedReason = reason.trim();
    Instant providerUpdateAt = Instant.now();
    docuSignGateway.voidEnvelope(existing.getExternalEnvelopeId(), trimmedReason);

    return transactionTemplate.execute(
        status -> {
          EsignEnvelopeEntity updated =
              envelopeRepository.save(
                  existing.toBuilder()
                      .status(EsignEnvelopeStatus.VOIDED)
                      .voidedReason(trimmedReason)
                      .lastProviderUpdateAt(providerUpdateAt)
                      .build());
          List<EsignSignerEntity> updatedSigners =
              existingSigners.stream()
                  .map(
                      signer ->
                          signerRepository.save(
                              signer.toBuilder()
                                  .status(EsignSignerStatus.VOIDED)
                                  .lastStatusAt(providerUpdateAt)
                                  .build()))
                  .toList();
          auditLogApi.recordUpdate(RESOURCE_TYPE, updated.getId(), existing, updated);
          return toResponse(updated, updatedSigners);
        });
  }

  @Override
  public EsignEnvelopeResponse resendEnvelope(Long id) {
    EsignEnvelopeEntity envelope = envelopeSupport.loadAccessibleEnvelope(id);
    List<EsignSignerEntity> signers = envelopeSupport.loadSigners(envelope.getId());
    List<EsignSignerEntity> actionableSigners = requireActionableSigners(envelope, signers);
    docuSignGateway.resendRecipients(
        envelope.getExternalEnvelopeId(),
        actionableSigners.stream().map(this::toResendSigner).toList());
    return toResponse(envelope, signers);
  }

  @Override
  public EsignEnvelopeResponse resendSigner(Long id, String roleKey) {
    EsignEnvelopeEntity envelope = envelopeSupport.loadAccessibleEnvelope(id);
    List<EsignSignerEntity> signers = envelopeSupport.loadSigners(envelope.getId());
    validateResendableEnvelope(envelope);
    String normalizedRoleKey = normalizeAnchorKey(roleKey);
    EsignSignerEntity signer =
        signers.stream()
            .filter(candidate -> candidate.getRoleKey().equals(normalizedRoleKey))
            .findFirst()
            .orElseThrow(
                () -> new NotFoundException("E-sign signer not found with roleKey: " + roleKey));
    if (!isActionableSigner(signer)) {
      throw ValidationErrorException.of(EsignResendErrorCode.SIGNER_NOT_ACTIONABLE);
    }
    docuSignGateway.resendRecipients(
        envelope.getExternalEnvelopeId(), List.of(toResendSigner(signer)));
    return toResponse(envelope, signers);
  }

  @Override
  public EsignEmbeddedViewResponse createEmbeddedSigningView(
      Long id, String roleKey, String locale) {
    EsignEnvelopeEntity envelope = envelopeSupport.loadAccessibleEnvelope(id);
    if (envelope.getDeliveryMode() != EsignDeliveryMode.IN_PERSON) {
      throw ValidationErrorException.of(EsignEmbeddedViewErrorCode.EMBEDDED_VIEW_IN_PERSON_ONLY);
    }
    String normalizedRoleKey = normalizeAnchorKey(roleKey);
    EsignSignerEntity signer =
        envelopeSupport.loadSigners(envelope.getId()).stream()
            .filter(candidate -> candidate.getRoleKey().equals(normalizedRoleKey))
            .findFirst()
            .orElseThrow(
                () -> new NotFoundException("E-sign signer not found with roleKey: " + roleKey));

    String signingUrl =
        docuSignGateway.createRecipientView(
            envelope.getExternalEnvelopeId(),
            new SignerRequest(
                signer.getRoleKey(),
                signer.getFullName(),
                signer.getEmail(),
                signer.getProviderRecipientId(),
                envelope.getDeliveryMode(),
                signer.getDeliveryMethod(),
                signer.getAuthMethod(),
                null,
                signer.getSmsNumber(),
                signer.getSignatureAnchorText(),
                signer.getDateAnchorText(),
                signer.getRoutingOrder()),
            locale);
    return EsignEmbeddedViewResponse.builder()
        .roleKey(signer.getRoleKey())
        .signingUrl(signingUrl)
        .build();
  }

  @Override
  public EsignDocumentDownload downloadCertificate(Long id) {
    return readStoredDocument(
        id,
        EsignEnvelopeEntity::getCertificateStorageObjectId,
        "Could not read stored signing certificate");
  }

  @Override
  public EsignDocumentDownload downloadSignedDocument(Long id) {
    return readStoredDocument(
        id, EsignEnvelopeEntity::getSignedStorageObjectId, "Could not read stored signed document");
  }

  private EsignDocumentDownload readStoredDocument(
      Long envelopeId,
      Function<EsignEnvelopeEntity, Long> storageObjectIdExtractor,
      String readErrorMessage) {
    EsignEnvelopeEntity existing = envelopeSupport.loadAccessibleEnvelope(envelopeId);
    Long storageObjectId = storageObjectIdExtractor.apply(existing);
    if (existing.getStatus() != EsignEnvelopeStatus.COMPLETED || storageObjectId == null) {
      throw ValidationErrorException.of(EsignDownloadErrorCode.FILE_NOT_READY);
    }
    StoredObject signedDocument = requireStoredObject(storageObjectId, envelopeId);
    try {
      return EsignDocumentDownload.builder()
          .fileName(signedDocument.fileName())
          .mimeType(signedDocument.mimeType())
          .contentSize(signedDocument.contentSize())
          .contentStream(storedObjectApi.read(signedDocument.id()))
          .build();
    } catch (IOException e) {
      throw new IllegalStateException(readErrorMessage, e);
    }
  }

  private EsignEnvelopeResponse toResponse(
      EsignEnvelopeEntity envelope, List<EsignSignerEntity> signers) {
    StoredObject sourceDocument =
        requireStoredObject(envelope.getSourceStorageObjectId(), envelope.getId());
    return EsignEnvelopeResponse.builder()
        .id(envelope.getId())
        .externalEnvelopeId(envelope.getExternalEnvelopeId())
        .subject(envelope.getSubject())
        .message(envelope.getMessage())
        .fileName(sourceDocument.fileName())
        .deliveryMode(envelope.getDeliveryMode())
        .status(envelope.getStatus())
        .tenantId(envelope.getTenantId())
        .remindersEnabled(envelope.isRemindersEnabled())
        .reminderIntervalHours(envelope.getReminderIntervalHours())
        .voidedReason(envelope.getVoidedReason())
        .completedAt(envelope.getCompletedAt())
        .lastProviderUpdateAt(envelope.getLastProviderUpdateAt())
        .certificateReady(envelope.getCertificateStorageObjectId() != null)
        .signedDocumentReady(envelope.getSignedStorageObjectId() != null)
        .signers(signers.stream().map(this::toSignerResponse).toList())
        .createdAt(envelope.getCreatedAt())
        .updatedAt(envelope.getUpdatedAt())
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
        .deliveryMethod(signer.getDeliveryMethod())
        .authMethod(signer.getAuthMethod())
        .providerRecipientId(signer.getProviderRecipientId())
        .status(signer.getStatus())
        .viewedAt(signer.getViewedAt())
        .completedAt(signer.getCompletedAt())
        .lastStatusAt(signer.getLastStatusAt())
        .build();
  }

  private List<EsignSignerEntity> requireActionableSigners(
      EsignEnvelopeEntity envelope, List<EsignSignerEntity> signers) {
    validateResendableEnvelope(envelope);
    List<EsignSignerEntity> actionableSigners =
        signers.stream().filter(this::isActionableSigner).toList();
    if (actionableSigners.isEmpty()) {
      throw ValidationErrorException.of(EsignResendErrorCode.NO_ACTIONABLE_SIGNERS);
    }
    return actionableSigners;
  }

  private void validateResendableEnvelope(EsignEnvelopeEntity envelope) {
    if (envelope.getDeliveryMode() != EsignDeliveryMode.REMOTE) {
      throw ValidationErrorException.of(EsignResendErrorCode.RESEND_REMOTE_ONLY);
    }
    if (envelope.getStatus() == EsignEnvelopeStatus.COMPLETED
        || envelope.getStatus() == EsignEnvelopeStatus.DECLINED
        || envelope.getStatus() == EsignEnvelopeStatus.VOIDED) {
      throw ValidationErrorException.of(EsignResendErrorCode.ENVELOPE_NOT_ACTIONABLE);
    }
  }

  private boolean isActionableSigner(EsignSignerEntity signer) {
    return signer.getStatus() == EsignSignerStatus.SENT
        || signer.getStatus() == EsignSignerStatus.DELIVERED;
  }

  private SignerRequest toResendSigner(EsignSignerEntity signer) {
    return new SignerRequest(
        signer.getRoleKey(),
        signer.getFullName(),
        signer.getEmail(),
        signer.getProviderRecipientId(),
        EsignDeliveryMode.REMOTE,
        signer.getDeliveryMethod(),
        signer.getAuthMethod(),
        null,
        signer.getSmsNumber(),
        signer.getSignatureAnchorText(),
        signer.getDateAnchorText(),
        signer.getRoutingOrder());
  }

  private PersistedPendingEnvelope persistPendingEnvelope(
      EsignCreateEnvelopeCommand command,
      StoredObject stored,
      List<EsignSignerEntity> signerEntities) {
    EsignEnvelopeEntity envelope =
        envelopeRepository.save(
            EsignEnvelopeEntity.builder()
                .externalEnvelopeId(null)
                .subject(command.getSubject().trim())
                .message(StringUtils.trimToNull(command.getMessage()))
                .deliveryMode(command.getDeliveryMode())
                .status(EsignEnvelopeStatus.CREATED)
                .tenantId(currentUserProvider.currentTenantId().orElse(null))
                .sourceStorageObjectId(stored.id())
                .remindersEnabled(command.isRemindersEnabled())
                .reminderIntervalHours(command.getReminderIntervalHours())
                .lastProviderUpdateAt(null)
                .build());

    for (EsignSignerEntity signer : signerEntities) {
      signerRepository.save(
          signer.toBuilder()
              .envelopeId(envelope.getId())
              .providerRecipientId(null)
              .status(EsignSignerStatus.CREATED)
              .lastStatusAt(null)
              .build());
    }
    return new PersistedPendingEnvelope(envelope.getId());
  }

  private EsignEnvelopeResponse finalizePendingEnvelope(
      Long envelopeId, CreateEnvelopeResult providerEnvelope) {
    EsignEnvelopeEntity existing = envelopeSupport.loadAccessibleEnvelope(envelopeId);
    List<EsignSignerEntity> existingSigners = envelopeSupport.loadSigners(existing.getId());
    Map<String, SignerResult> providerSignersByRole =
        providerEnvelope.signers().stream()
            .collect(
                Collectors.toMap(
                    SignerResult::roleKey,
                    Function.identity(),
                    (left, right) -> left,
                    LinkedHashMap::new));

    EsignEnvelopeEntity updatedEnvelope =
        envelopeRepository.save(
            existing.toBuilder()
                .externalEnvelopeId(providerEnvelope.externalEnvelopeId())
                .status(providerEnvelope.envelopeStatus())
                .lastProviderUpdateAt(providerEnvelope.providerTimestamp())
                .build());

    List<EsignSignerEntity> updatedSigners = new ArrayList<>();
    for (EsignSignerEntity signer : existingSigners) {
      SignerResult providerSigner = providerSignersByRole.get(signer.getRoleKey());
      updatedSigners.add(
          signerRepository.save(
              signer.toBuilder()
                  .providerRecipientId(
                      providerSigner == null ? null : providerSigner.providerRecipientId())
                  .status(providerSigner == null ? signer.getStatus() : providerSigner.status())
                  .lastStatusAt(providerEnvelope.providerTimestamp())
                  .build()));
    }
    auditLogApi.recordCreate(RESOURCE_TYPE, updatedEnvelope.getId(), updatedEnvelope);
    return toResponse(updatedEnvelope, updatedSigners);
  }

  private void validateCreateCommand(EsignCreateEnvelopeCommand command) {
    if (command == null || command.getContentStream() == null) {
      throw ValidationErrorException.of(EsignCreateErrorCode.FILE_REQUIRED);
    }
    if (StringUtils.isBlank(command.getOriginalFilename())) {
      throw ValidationErrorException.of(EsignCreateErrorCode.FILE_REQUIRED);
    }
    if (StringUtils.isBlank(command.getSubject())) {
      throw ValidationErrorException.of(EsignCreateErrorCode.SUBJECT_REQUIRED);
    }
    if (command.getSigners() == null || command.getSigners().isEmpty()) {
      throw ValidationErrorException.of(EsignCreateErrorCode.SIGNERS_REQUIRED);
    }
    if (command.getDeliveryMode() == EsignDeliveryMode.REMOTE) {
      if (command.isRemindersEnabled() && command.getReminderIntervalHours() == null) {
        throw ValidationErrorException.of(EsignCreateErrorCode.REMINDER_INTERVAL_REQUIRED);
      }
    } else if (command.isRemindersEnabled()) {
      throw ValidationErrorException.of(EsignCreateErrorCode.REMINDERS_REMOTE_ONLY);
    }
  }

  private void validateAnchors(
      List<EsignCreateEnvelopeRequest.SignerInput> signers,
      PdfAnchorParseResult anchorParseResult) {
    LinkedHashSet<String> seenAnchorKeys = new LinkedHashSet<>();
    for (EsignCreateEnvelopeRequest.SignerInput signer : signers) {
      String anchorKey = normalizeAnchorKey(signer.anchorKey());
      if (anchorKey == null) {
        throw ValidationErrorException.of(EsignCreateErrorCode.INVALID_ANCHOR_KEY);
      }
      if (!seenAnchorKeys.add(anchorKey)) {
        throw ValidationErrorException.of(EsignCreateErrorCode.DUPLICATE_ANCHOR_KEY);
      }
      if (!anchorParseResult.signatureAnchorKeys().contains(anchorKey)) {
        throw ValidationErrorException.of(EsignCreateErrorCode.MISSING_SIGNATURE_ANCHOR);
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
      EsignSignerDeliveryMethod deliveryMethod =
          resolveDeliveryMethod(command.getDeliveryMode(), signer);
      String normalizedSmsNumber =
          signer.authMethod() == EsignAuthMethod.SMS
                  || deliveryMethod == EsignSignerDeliveryMethod.SMS
              ? normalizeSmsNumber(signer.smsNumber())
              : null;
      int resolvedRoutingOrder =
          signer.routingOrder() == null ? defaultRoutingOrder : signer.routingOrder().intValue();
      entities.add(
          EsignSignerEntity.builder()
              .roleKey(anchorKey)
              .signatureAnchorText("/" + anchorKey + "/")
              .dateAnchorText(
                  anchorParseResult.dateAnchorKeys().contains(dateAnchorKey)
                      ? "/" + dateAnchorKey + "/"
                      : null)
              .routingOrder(resolvedRoutingOrder)
              .fullName(signer.fullName().trim())
              .email(StringUtils.trimToNull(signer.email()))
              .deliveryMethod(deliveryMethod)
              .authMethod(signer.authMethod())
              .smsNumber(normalizedSmsNumber)
              .status(EsignSignerStatus.SENT)
              .build());
      defaultRoutingOrder++;
    }
    return entities.stream()
        .sorted(
            Comparator.comparing(EsignSignerEntity::getRoutingOrder)
                .thenComparing(EsignSignerEntity::getRoleKey))
        .toList();
  }

  private EsignCreateEnvelopeRequest.SignerInput signerForRole(
      List<EsignCreateEnvelopeRequest.SignerInput> signers, String roleKey) {
    return signers.stream()
        .filter(signer -> roleKey.equals(normalizeAnchorKey(signer.anchorKey())))
        .findFirst()
        .orElseThrow();
  }

  private void validateSigner(
      EsignDeliveryMode deliveryMode, EsignCreateEnvelopeRequest.SignerInput signer) {
    if (deliveryMode == EsignDeliveryMode.REMOTE) {
      EsignSignerDeliveryMethod resolvedDeliveryMethod =
          resolveDeliveryMethod(deliveryMode, signer);
      if (resolvedDeliveryMethod == EsignSignerDeliveryMethod.EMAIL
          && StringUtils.isBlank(signer.email())) {
        throw ValidationErrorException.of(EsignCreateErrorCode.REMOTE_EMAIL_REQUIRED);
      }
      if (signer.authMethod() == EsignAuthMethod.PASSCODE
          && StringUtils.isBlank(signer.passcode())) {
        throw ValidationErrorException.of(EsignCreateErrorCode.PASSCODE_REQUIRED);
      }
      if ((signer.authMethod() == EsignAuthMethod.SMS
              || resolvedDeliveryMethod == EsignSignerDeliveryMethod.SMS)
          && StringUtils.isBlank(signer.smsNumber())) {
        throw ValidationErrorException.of(EsignCreateErrorCode.SMS_NUMBER_REQUIRED);
      }
      if (resolvedDeliveryMethod == EsignSignerDeliveryMethod.SMS
          && signer.authMethod() == EsignAuthMethod.SMS) {
        throw ValidationErrorException.of(EsignCreateErrorCode.SMS_DELIVERY_SMS_AUTH_NOT_SUPPORTED);
      }
      return;
    }
    if (signer.authMethod() != EsignAuthMethod.NONE) {
      throw ValidationErrorException.of(EsignCreateErrorCode.IN_PERSON_AUTH_NOT_ALLOWED);
    }
  }

  private EsignSignerDeliveryMethod resolveDeliveryMethod(
      EsignDeliveryMode deliveryMode, EsignCreateEnvelopeRequest.SignerInput signer) {
    if (deliveryMode != EsignDeliveryMode.REMOTE) {
      return null;
    }
    return signer.deliveryMethod() == null
        ? EsignSignerDeliveryMethod.EMAIL
        : signer.deliveryMethod();
  }

  private static String normalizeAnchorKey(String rawAnchorKey) {
    String normalized = StringUtils.trimToNull(rawAnchorKey);
    if (normalized == null) {
      return null;
    }
    normalized = normalized.toLowerCase(Locale.ROOT);
    return normalized.matches("s\\d+") ? normalized : null;
  }

  private String normalizeSmsNumber(String rawSmsNumber) {
    String value = StringUtils.trimToNull(rawSmsNumber);
    if (value == null) {
      return null;
    }

    if (value.startsWith("+")) {
      String digits = "+" + value.substring(1).replaceAll("\\D", "");
      if (digits.matches("^\\+[1-9]\\d{7,14}$")) {
        return digits;
      }
      throw ValidationErrorException.of(EsignCreateErrorCode.SMS_NUMBER_INVALID);
    }

    String digits = value.replaceAll("\\D", "");
    if (digits.length() == 10) {
      return "+" + DEFAULT_SMS_COUNTRY_CODE + digits;
    }
    if (digits.length() == 11 && digits.startsWith(DEFAULT_SMS_COUNTRY_CODE)) {
      return "+" + digits;
    }
    throw ValidationErrorException.of(EsignCreateErrorCode.SMS_NUMBER_INVALID);
  }

  private byte[] readAllBytes(InputStream inputStream) {
    try (InputStream source = inputStream) {
      return source.readAllBytes();
    } catch (IOException e) {
      throw ValidationErrorException.of(EsignCreateErrorCode.FILE_READ_FAILED);
    }
  }

  private StoredObject writeToStorage(
      String namespace, String filename, String mimeType, byte[] bytes) {
    return writeToStorage(namespace, filename, mimeType, new ByteArrayInputStream(bytes));
  }

  private StoredObject writeToStorage(
      String namespace, String filename, String mimeType, InputStream contentStream) {
    return storedObjectApi.store(
        StoreObjectRequest.builder()
            .namespace(namespace)
            .fileName(filename)
            .mimeType(mimeType)
            .contentStream(contentStream)
            .build());
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

  private void cleanupPendingEnvelope(Long envelopeId, Long storageObjectId) {
    try {
      transactionTemplate.execute(
          status -> {
            envelopeRepository
                .findById(envelopeId)
                .ifPresent(
                    envelope -> {
                      signerRepository.deleteAll(signerRepository.findByEnvelopeId(envelopeId));
                      envelopeRepository.delete(envelope);
                    });
            return null;
          });
    } finally {
      cleanupStorageQuietly(storageObjectId);
    }
  }

  private boolean tryVoidEnvelopeQuietly(String externalEnvelopeId, String reason) {
    if (StringUtils.isBlank(externalEnvelopeId)) {
      return false;
    }
    try {
      docuSignGateway.voidEnvelope(externalEnvelopeId, reason);
      return true;
    } catch (RuntimeException ignored) {
      return false;
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

  private record PersistedPendingEnvelope(Long envelopeId) {}
}
