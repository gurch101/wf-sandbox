package com.gurch.sandbox.esign.internal;

import com.gurch.sandbox.esign.DownloadEsignArtifactsRequest;
import com.gurch.sandbox.esign.DownloadEsignArtifactsResponse;
import com.gurch.sandbox.esign.EsignApi;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignRemoteDeliveryMethod;
import com.gurch.sandbox.esign.EsignSignatureMode;
import com.gurch.sandbox.esign.EsignWebhookStatusUpdate;
import com.gurch.sandbox.esign.ResendEsignEnvelopeRequest;
import com.gurch.sandbox.esign.StartEmbeddedEsignRequest;
import com.gurch.sandbox.esign.StartEsignResponse;
import com.gurch.sandbox.esign.StartRemoteEsignRequest;
import com.gurch.sandbox.esign.VoidEsignEnvelopeRequest;
import com.gurch.sandbox.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Builder;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
class DefaultEsignService implements EsignApi {

  private final Clock clock;
  private final CurrentUserProvider currentUserProvider;
  private final TenantDocusignConfigRepository tenantDocusignConfigRepository;
  private final PdfAnchorMarkerResolver pdfAnchorMarkerResolver;
  private final DocusignEsignGateway docusignEsignGateway;

  DefaultEsignService(
      Clock clock,
      CurrentUserProvider currentUserProvider,
      TenantDocusignConfigRepository tenantDocusignConfigRepository,
      PdfAnchorMarkerResolver pdfAnchorMarkerResolver,
      DocusignEsignGateway docusignEsignGateway) {
    this.clock = clock;
    this.currentUserProvider = currentUserProvider;
    this.tenantDocusignConfigRepository = tenantDocusignConfigRepository;
    this.pdfAnchorMarkerResolver = pdfAnchorMarkerResolver;
    this.docusignEsignGateway = docusignEsignGateway;
  }

  @Override
  public StartEsignResponse startRemote(StartRemoteEsignRequest request) {
    validateRemoteRequest(request);
    StartCommand command =
        StartCommand.builder()
            .requestId(request.getRequestId())
            .documentName(request.getDocumentName())
            .documentPdf(request.getDocumentPdf())
            .idempotencyKey(request.getIdempotencyKey())
            .emailSubject(request.getEmailSubject())
            .emailMessage(request.getEmailMessage())
            .build();

    List<StartRemoteEsignRequest.SignerRecipient> sortedSigners =
        request.getSigners().stream()
            .sorted(Comparator.comparing(StartRemoteEsignRequest.SignerRecipient::getRoutingOrder))
            .toList();
    List<EnvelopeSigner> signers =
        sortedSigners.stream()
            .map(
                signer ->
                    new EnvelopeSigner(
                        signer.getRecipientId(),
                        signer.getName(),
                        signer.getEmail(),
                        signer.getRoutingOrder(),
                        signer.getRole(),
                        signer.getRemoteDeliveryMethod() == null
                            ? EsignRemoteDeliveryMethod.EMAIL
                            : signer.getRemoteDeliveryMethod(),
                        signer.getSmsCountryCode(),
                        signer.getSmsNumber(),
                        signer.getAccessCode()))
            .toList();
    List<EnvelopeCc> ccRecipients =
        (request.getCcRecipients() == null
                ? List.<StartRemoteEsignRequest.CcRecipient>of()
                : request.getCcRecipients())
            .stream()
                .map(
                    cc ->
                        new EnvelopeCc(
                            cc.getRecipientId(), cc.getName(), cc.getEmail(), cc.getRoutingOrder()))
                .toList();

    return start(command, EsignSignatureMode.REMOTE, signers, ccRecipients);
  }

  @Override
  public StartEsignResponse startEmbedded(StartEmbeddedEsignRequest request) {
    validateEmbeddedRequest(request);
    StartCommand command =
        StartCommand.builder()
            .requestId(request.getRequestId())
            .documentName(request.getDocumentName())
            .documentPdf(request.getDocumentPdf())
            .idempotencyKey(request.getIdempotencyKey())
            .emailSubject(request.getEmailSubject())
            .emailMessage(request.getEmailMessage())
            .build();

    List<StartEmbeddedEsignRequest.SignerRecipient> sortedSigners =
        request.getSigners().stream()
            .sorted(
                Comparator.comparing(StartEmbeddedEsignRequest.SignerRecipient::getRoutingOrder))
            .toList();
    List<EnvelopeSigner> signers =
        sortedSigners.stream()
            .map(
                signer ->
                    new EnvelopeSigner(
                        signer.getRecipientId(),
                        signer.getName(),
                        signer.getEmail(),
                        signer.getRoutingOrder(),
                        signer.getRole(),
                        EsignRemoteDeliveryMethod.EMAIL,
                        null,
                        null,
                        null))
            .toList();
    List<EnvelopeCc> ccRecipients =
        (request.getCcRecipients() == null
                ? List.<StartEmbeddedEsignRequest.CcRecipient>of()
                : request.getCcRecipients())
            .stream()
                .map(
                    cc ->
                        new EnvelopeCc(
                            cc.getRecipientId(), cc.getName(), cc.getEmail(), cc.getRoutingOrder()))
                .toList();

    return start(command, EsignSignatureMode.EMBEDDED, signers, ccRecipients);
  }

  private StartEsignResponse start(
      StartCommand command,
      EsignSignatureMode signatureMode,
      List<EnvelopeSigner> signers,
      List<EnvelopeCc> ccRecipients) {
    TenantDocusignConfigEntity config = requireActiveTenantConfig();
    Set<AnchorToken> availableAnchors = pdfAnchorMarkerResolver.resolve(command.getDocumentPdf());
    DocusignEnvelopeRequest envelopeRequest =
        toEnvelopeRequest(command, signatureMode, signers, ccRecipients, availableAnchors);
    DocusignEnvelopeResult envelopeResult =
        docusignEsignGateway.createEnvelope(config, envelopeRequest);
    Instant createdAt = Instant.now(clock);
    return StartEsignResponse.builder()
        .requestId(command.getRequestId())
        .envelopeId(envelopeResult.envelopeId())
        .signatureMode(signatureMode)
        .status(EsignEnvelopeStatus.CREATED)
        .recipientViewUrl(envelopeResult.recipientViewUrl())
        .createdAt(createdAt)
        .build();
  }

  @Override
  public void voidEnvelope(VoidEsignEnvelopeRequest request) {
    requireNonNull(request, "request is required");
    requireHasText(request.getEnvelopeId(), "envelopeId is required");
    requireHasText(request.getReason(), "reason is required");
    TenantDocusignConfigEntity config = requireActiveTenantConfig();
    docusignEsignGateway.voidEnvelope(config, request.getEnvelopeId(), request.getReason());
  }

  @Override
  public void resendEnvelope(ResendEsignEnvelopeRequest request) {
    requireNonNull(request, "request is required");
    requireHasText(request.getEnvelopeId(), "envelopeId is required");
    TenantDocusignConfigEntity config = requireActiveTenantConfig();
    docusignEsignGateway.resendEnvelope(config, request.getEnvelopeId());
  }

  @Override
  public DownloadEsignArtifactsResponse downloadArtifacts(DownloadEsignArtifactsRequest request) {
    requireNonNull(request, "request is required");
    requireHasText(request.getEnvelopeId(), "envelopeId is required");
    TenantDocusignConfigEntity config = requireActiveTenantConfig();
    DocusignEnvelopeArtifacts artifacts =
        docusignEsignGateway.downloadArtifacts(config, request.getEnvelopeId());
    return DownloadEsignArtifactsResponse.builder()
        .signedDocumentPdf(artifacts.getSignedDocumentPdf())
        .certificatePdf(artifacts.getCertificatePdf())
        .build();
  }

  @Override
  public void handleWebhookStatusUpdate(EsignWebhookStatusUpdate update, String signatureHeader) {
    requireNonNull(update, "update is required");
    requireHasText(update.getEventId(), "eventId is required");
    requireHasText(update.getEnvelopeId(), "envelopeId is required");
    requireHasText(update.getStatus(), "status is required");
    log.info(
        "Received DocuSign webhook status update eventId={} envelopeId={} status={} at={} signaturePresent={}",
        update.getEventId(),
        update.getEnvelopeId(),
        update.getStatus(),
        update.getStatusChangedAt(),
        signatureHeader != null && !signatureHeader.isBlank());
  }

  private DocusignEnvelopeRequest toEnvelopeRequest(
      StartCommand command,
      EsignSignatureMode signatureMode,
      List<EnvelopeSigner> signers,
      List<EnvelopeCc> ccRecipients,
      Set<AnchorToken> availableAnchors) {
    DocusignEnvelopeRequest.DocusignEnvelopeRequestBuilder builder =
        DocusignEnvelopeRequest.builder()
            .requestId(command.getRequestId())
            .signatureMode(signatureMode)
            .documentName(command.getDocumentName())
            .documentPdf(command.getDocumentPdf())
            .idempotencyKey(command.getIdempotencyKey())
            .emailSubject(command.getEmailSubject())
            .emailMessage(command.getEmailMessage());

    for (int i = 0; i < signers.size(); i++) {
      EnvelopeSigner signer = signers.get(i);
      String signAnchor = requiredSignatureAnchor(i + 1, availableAnchors);
      String dateAnchor = optionalDateAnchor(i + 1, availableAnchors).orElse(null);
      builder.signer(
          DocusignEnvelopeRequest.SignerRecipient.builder()
              .recipientId(signer.recipientId())
              .name(signer.name())
              .email(signer.email())
              .routingOrder(signer.routingOrder())
              .role(signer.role())
              .signAnchor(signAnchor)
              .dateAnchor(dateAnchor)
              .remoteDeliveryMethod(signer.remoteDeliveryMethod())
              .smsCountryCode(signer.smsCountryCode())
              .smsNumber(signer.smsNumber())
              .accessCode(signer.accessCode())
              .build());
    }

    for (EnvelopeCc cc : ccRecipients) {
      builder.ccRecipient(
          DocusignEnvelopeRequest.CcRecipient.builder()
              .recipientId(cc.recipientId())
              .name(cc.name())
              .email(cc.email())
              .routingOrder(cc.routingOrder())
              .build());
    }
    return builder.build();
  }

  private void validateRemoteRequest(StartRemoteEsignRequest request) {
    validateCommon(request.getRequestId(), request.getDocumentPdf());
    List<StartRemoteEsignRequest.SignerRecipient> signers = request.getSigners();
    if (signers == null || signers.isEmpty()) {
      throw new IllegalArgumentException("at least one signer is required");
    }
    if (signers.size() > 2) {
      throw new IllegalArgumentException("only up to 2 signers are currently supported");
    }
    signers.forEach(this::validateRemoteSigner);
    List<StartRemoteEsignRequest.CcRecipient> ccRecipients =
        request.getCcRecipients() == null ? List.of() : request.getCcRecipients();
    ccRecipients.forEach(this::validateRemoteCcRecipient);
  }

  private void validateEmbeddedRequest(StartEmbeddedEsignRequest request) {
    validateCommon(request.getRequestId(), request.getDocumentPdf());
    List<StartEmbeddedEsignRequest.SignerRecipient> signers = request.getSigners();
    if (signers == null || signers.isEmpty()) {
      throw new IllegalArgumentException("at least one signer is required");
    }
    if (signers.size() > 2) {
      throw new IllegalArgumentException("only up to 2 signers are currently supported");
    }
    signers.forEach(this::validateEmbeddedSigner);
    List<StartEmbeddedEsignRequest.CcRecipient> ccRecipients =
        request.getCcRecipients() == null ? List.of() : request.getCcRecipients();
    ccRecipients.forEach(this::validateEmbeddedCcRecipient);
  }

  private void validateCommon(Long requestId, byte[] documentPdf) {
    requireNonNull(requestId, "requestId is required");
    if (documentPdf == null || documentPdf.length == 0) {
      throw new IllegalArgumentException("documentPdf is required");
    }
  }

  private void validateRemoteSigner(StartRemoteEsignRequest.SignerRecipient signer) {
    requireNonNull(signer, "signer entry is required");
    requireHasText(signer.getRecipientId(), "signer.recipientId is required");
    requireHasText(signer.getName(), "signer.name is required");
    requireNonNull(signer.getRoutingOrder(), "signer.routingOrder is required");
    if (signer.getRoutingOrder() <= 0) {
      throw new IllegalArgumentException("signer.routingOrder must be > 0");
    }

    boolean hasSmsCountry = hasText(signer.getSmsCountryCode());
    boolean hasSmsNumber = hasText(signer.getSmsNumber());
    if (hasSmsCountry != hasSmsNumber) {
      throw new IllegalArgumentException(
          "signer.smsCountryCode and signer.smsNumber must be provided together");
    }

    EsignRemoteDeliveryMethod method =
        signer.getRemoteDeliveryMethod() == null
            ? EsignRemoteDeliveryMethod.EMAIL
            : signer.getRemoteDeliveryMethod();
    if (method == EsignRemoteDeliveryMethod.EMAIL && !hasText(signer.getEmail())) {
      throw new IllegalArgumentException("signer.email is required for remote EMAIL delivery");
    }
    if (method == EsignRemoteDeliveryMethod.SMS && (!hasSmsCountry || !hasSmsNumber)) {
      throw new IllegalArgumentException(
          "signer.smsCountryCode and signer.smsNumber are required for remote SMS delivery");
    }
  }

  private void validateEmbeddedSigner(StartEmbeddedEsignRequest.SignerRecipient signer) {
    requireNonNull(signer, "signer entry is required");
    requireHasText(signer.getRecipientId(), "signer.recipientId is required");
    requireHasText(signer.getName(), "signer.name is required");
    requireHasText(signer.getEmail(), "signer.email is required");
    requireNonNull(signer.getRoutingOrder(), "signer.routingOrder is required");
    if (signer.getRoutingOrder() <= 0) {
      throw new IllegalArgumentException("signer.routingOrder must be > 0");
    }
  }

  private void validateRemoteCcRecipient(StartRemoteEsignRequest.CcRecipient recipient) {
    requireNonNull(recipient, "ccRecipient entry is required");
    requireHasText(recipient.getRecipientId(), "ccRecipient.recipientId is required");
    requireHasText(recipient.getName(), "ccRecipient.name is required");
    requireHasText(recipient.getEmail(), "ccRecipient.email is required");
    requireNonNull(recipient.getRoutingOrder(), "ccRecipient.routingOrder is required");
    if (recipient.getRoutingOrder() <= 0) {
      throw new IllegalArgumentException("ccRecipient.routingOrder must be > 0");
    }
  }

  private void validateEmbeddedCcRecipient(StartEmbeddedEsignRequest.CcRecipient recipient) {
    requireNonNull(recipient, "ccRecipient entry is required");
    requireHasText(recipient.getRecipientId(), "ccRecipient.recipientId is required");
    requireHasText(recipient.getName(), "ccRecipient.name is required");
    requireHasText(recipient.getEmail(), "ccRecipient.email is required");
    requireNonNull(recipient.getRoutingOrder(), "ccRecipient.routingOrder is required");
    if (recipient.getRoutingOrder() <= 0) {
      throw new IllegalArgumentException("ccRecipient.routingOrder must be > 0");
    }
  }

  private String requiredSignatureAnchor(int signerIndex, Set<AnchorToken> availableAnchors) {
    AnchorToken token = signerIndex == 1 ? AnchorToken.S1 : AnchorToken.S2;
    if (!availableAnchors.contains(token)) {
      throw new IllegalArgumentException(
          "PDF is missing required signature anchor: " + token.token());
    }
    return token.token();
  }

  private Optional<String> optionalDateAnchor(int signerIndex, Set<AnchorToken> availableAnchors) {
    AnchorToken token = signerIndex == 1 ? AnchorToken.D1 : AnchorToken.D2;
    return availableAnchors.contains(token) ? Optional.of(token.token()) : Optional.empty();
  }

  private TenantDocusignConfigEntity requireActiveTenantConfig() {
    Integer tenantId =
        currentUserProvider
            .currentTenantId()
            .orElseThrow(() -> new IllegalArgumentException("current tenant is required"));
    return tenantDocusignConfigRepository
        .findByTenantIdAndActiveTrue(tenantId)
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "active DocuSign configuration is missing for tenant %s".formatted(tenantId)));
  }

  private static <T> T requireNonNull(T value, String message) {
    return Objects.requireNonNull(value, message);
  }

  private static void requireHasText(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  @Value
  @Builder
  private static class StartCommand {
    Long requestId;
    String documentName;
    byte[] documentPdf;
    String idempotencyKey;
    String emailSubject;
    String emailMessage;
  }

  private record EnvelopeSigner(
      String recipientId,
      String name,
      String email,
      Integer routingOrder,
      String role,
      EsignRemoteDeliveryMethod remoteDeliveryMethod,
      String smsCountryCode,
      String smsNumber,
      String accessCode) {}

  private record EnvelopeCc(String recipientId, String name, String email, Integer routingOrder) {}
}
