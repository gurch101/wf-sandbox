package com.gurch.sandbox.esign.internal;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.client.auth.OAuth;
import com.docusign.esign.model.DateSigned;
import com.docusign.esign.model.Document;
import com.docusign.esign.model.Envelope;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.EnvelopeIdsRequest;
import com.docusign.esign.model.EnvelopeSummary;
import com.docusign.esign.model.EnvelopesInformation;
import com.docusign.esign.model.InPersonSigner;
import com.docusign.esign.model.Notification;
import com.docusign.esign.model.RecipientPhoneNumber;
import com.docusign.esign.model.RecipientSMSAuthentication;
import com.docusign.esign.model.RecipientViewRequest;
import com.docusign.esign.model.Recipients;
import com.docusign.esign.model.Reminders;
import com.docusign.esign.model.SignHere;
import com.docusign.esign.model.Signer;
import com.docusign.esign.model.Tabs;
import com.docusign.esign.model.ViewUrl;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerDeliveryMethod;
import com.gurch.sandbox.esign.EsignSignerStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

public interface DocuSignGateway {
  CreateEnvelopeResult createEnvelope(CreateEnvelopeRequest request);

  void voidEnvelope(String externalEnvelopeId, String reason);

  void resendRecipients(String externalEnvelopeId, List<SignerRequest> signers);

  List<EnvelopeStatusResult> listEnvelopeStatuses(List<String> externalEnvelopeIds);

  String createRecipientView(String externalEnvelopeId, SignerRequest signer, String locale);

  DownloadedDocument downloadCompletedDocument(
      String externalEnvelopeId, String fileName, byte[] sourceDocument);

  DownloadedCertificate downloadCertificate(
      String externalEnvelopeId, String envelopeSubject, List<SignerSnapshot> signers);
}

@Value
class CreateEnvelopeRequest {
  String subject;
  String message;
  EsignDeliveryMode deliveryMode;
  String fileName;
  byte[] documentBytes;
  List<SignerRequest> signers;
  boolean remindersEnabled;
  Integer reminderIntervalHours;

  CreateEnvelopeRequest(
      String subject,
      String message,
      EsignDeliveryMode deliveryMode,
      String fileName,
      byte[] documentBytes,
      List<SignerRequest> signers,
      boolean remindersEnabled,
      Integer reminderIntervalHours) {
    this.subject = subject;
    this.message = message;
    this.deliveryMode = deliveryMode;
    this.fileName = fileName;
    this.documentBytes =
        documentBytes == null ? new byte[0] : Arrays.copyOf(documentBytes, documentBytes.length);
    this.signers = signers == null ? List.of() : List.copyOf(signers);
    this.remindersEnabled = remindersEnabled;
    this.reminderIntervalHours = reminderIntervalHours;
  }

  public byte[] getDocumentBytes() {
    return Arrays.copyOf(documentBytes, documentBytes.length);
  }
}

record SignerRequest(
    String roleKey,
    String fullName,
    String email,
    String providerRecipientId,
    EsignDeliveryMode deliveryMode,
    EsignSignerDeliveryMethod deliveryMethod,
    EsignAuthMethod authMethod,
    String accessCode,
    String smsNumber,
    String signatureAnchorText,
    String dateAnchorText,
    Integer routingOrder) {}

record CreateEnvelopeResult(
    String externalEnvelopeId,
    EsignEnvelopeStatus envelopeStatus,
    Instant providerTimestamp,
    List<SignerResult> signers) {

  CreateEnvelopeResult {
    signers = signers == null ? List.of() : List.copyOf(signers);
  }
}

record SignerResult(String roleKey, String providerRecipientId, EsignSignerStatus status) {}

record EnvelopeStatusResult(
    String externalEnvelopeId,
    EsignEnvelopeStatus envelopeStatus,
    Instant providerTimestamp,
    String voidedReason) {}

record SignerSnapshot(String fullName, String email, String roleKey, Instant completedAt) {}

@Value
class DownloadedCertificate {
  String fileName;
  String mimeType;
  byte[] content;

  DownloadedCertificate(String fileName, String mimeType, byte[] content) {
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
  }

  public byte[] getContent() {
    return Arrays.copyOf(content, content.length);
  }
}

@Value
class DownloadedDocument {
  String fileName;
  String mimeType;
  byte[] content;

  DownloadedDocument(String fileName, String mimeType, byte[] content) {
    this.fileName = fileName;
    this.mimeType = mimeType;
    this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
  }

  public byte[] getContent() {
    return Arrays.copyOf(content, content.length);
  }
}

@Data
@Component
@ConfigurationProperties(prefix = "docusign")
class DocuSignProperties {
  private String accountId;
  private String oauthBasePath;
  private String integrationKey;
  private String userId;
  private String privateKey;
  private String privateKeyPath;
  private long jwtTokenLifetimeSeconds = 3600;
  private String webhookHmacSecret;
  private String returnUrl;
  private String hostEmail;
  private String hostName;
}

@Component
@RequiredArgsConstructor
class DocuSignAuthProvider {

  private static final List<String> JWT_SCOPES = List.of("signature", "impersonation");
  private static final long REFRESH_SKEW_SECONDS = 60;

  private final DocuSignProperties properties;

  private volatile AuthSession cachedSession;

  AuthSession currentSession() {
    validateJwtConfiguration();
    AuthSession current = cachedSession;
    if (isUsable(current)) {
      return current;
    }
    synchronized (this) {
      current = cachedSession;
      if (isUsable(current)) {
        return current;
      }
      cachedSession = requestJwtSession();
      return cachedSession;
    }
  }

  private boolean isUsable(AuthSession session) {
    return session != null
        && StringUtils.isNotBlank(session.accessToken())
        && Instant.now().isBefore(session.expiresAt().minusSeconds(REFRESH_SKEW_SECONDS));
  }

  private void validateJwtConfiguration() {
    if (StringUtils.isBlank(properties.getIntegrationKey())
        || StringUtils.isBlank(properties.getOauthBasePath())
        || StringUtils.isBlank(properties.getUserId())
        || (StringUtils.isBlank(properties.getPrivateKey())
            && StringUtils.isBlank(properties.getPrivateKeyPath()))) {
      throw new IllegalStateException(
          "DocuSign JWT mode requires docusign.oauth-base-path, docusign.integration-key, docusign.user-id, "
              + "and either docusign.private-key or docusign.private-key-path");
    }
  }

  private AuthSession requestJwtSession() {
    try {
      ApiClient authClient = new ApiClient();
      authClient.setOAuthBasePath(properties.getOauthBasePath());
      OAuth.OAuthToken token =
          authClient.requestJWTUserToken(
              properties.getIntegrationKey().trim(),
              properties.getUserId().trim(),
              JWT_SCOPES,
              loadPrivateKeyBytes(),
              properties.getJwtTokenLifetimeSeconds());
      OAuth.UserInfo userInfo = authClient.getUserInfo(token.getAccessToken());
      OAuth.Account account = selectAccount(userInfo);
      return new AuthSession(
          token.getAccessToken(),
          Instant.now()
              .plusSeconds(
                  Math.max(300, token.getExpiresIn() == null ? 3600 : token.getExpiresIn())),
          account.getAccountId(),
          normalizeBasePath(account.getBaseUri()),
          StringUtils.trimToNull(userInfo.getEmail()),
          StringUtils.trimToNull(userInfo.getName()));
    } catch (ApiException e) {
      if (StringUtils.containsIgnoreCase(e.getMessage(), "consent_required")) {
        throw new IllegalStateException(
            "DocuSign JWT consent is required for the configured user. Grant consent for "
                + "integration key "
                + properties.getIntegrationKey()
                + " and scopes signature impersonation before retrying.",
            e);
      }
      throw new IllegalStateException("Could not obtain DocuSign JWT access token", e);
    } catch (IOException e) {
      throw new IllegalStateException("Could not load DocuSign JWT private key", e);
    }
  }

  private byte[] loadPrivateKeyBytes() throws IOException {
    if (StringUtils.isNotBlank(properties.getPrivateKey())) {
      return properties.getPrivateKey().replace("\\n", "\n").getBytes(StandardCharsets.UTF_8);
    }
    return Files.readAllBytes(Path.of(properties.getPrivateKeyPath().trim()));
  }

  private OAuth.Account selectAccount(OAuth.UserInfo userInfo) {
    List<OAuth.Account> accounts = userInfo == null ? List.of() : userInfo.getAccounts();
    if (accounts == null || accounts.isEmpty()) {
      throw new IllegalStateException(
          "DocuSign userinfo returned no accounts for the configured user");
    }
    if (StringUtils.isNotBlank(properties.getAccountId())) {
      return accounts.stream()
          .filter(
              account -> Objects.equals(properties.getAccountId().trim(), account.getAccountId()))
          .findFirst()
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "Configured docusign.account-id was not found in DocuSign userinfo response"));
    }
    return accounts.stream()
        .filter(account -> "true".equalsIgnoreCase(account.getIsDefault()))
        .findFirst()
        .orElse(accounts.get(0));
  }

  private String normalizeBasePath(String baseUri) {
    if (StringUtils.isBlank(baseUri)) {
      throw new IllegalStateException(
          "DocuSign userinfo did not return an account baseUri for the selected account");
    }
    String trimmed = baseUri.trim();
    return trimmed.endsWith("/restapi") ? trimmed : trimmed + "/restapi";
  }

  record AuthSession(
      String accessToken,
      Instant expiresAt,
      String accountId,
      String basePath,
      String email,
      String name) {}
}

@Component
@RequiredArgsConstructor
class DocuSignSdkGateway implements DocuSignGateway {

  private final DocuSignProperties properties;
  private final DocuSignAuthProvider authProvider;

  @Override
  public CreateEnvelopeResult createEnvelope(CreateEnvelopeRequest request) {
    try {
      EnvelopesApi envelopesApi = envelopesApi();
      EnvelopeDefinition definition = new EnvelopeDefinition();
      definition.setEmailSubject(request.getSubject());
      definition.setEmailBlurb(StringUtils.trimToNull(request.getMessage()));
      definition.setAllowReassign("false");
      definition.setStatus("sent");
      definition.setDocuments(
          List.of(toDocument(request.getFileName(), request.getDocumentBytes())));
      definition.setRecipients(toRecipients(request));
      if (request.getDeliveryMode() == EsignDeliveryMode.REMOTE && request.isRemindersEnabled()) {
        definition.setNotification(toNotification(request.getReminderIntervalHours()));
      }

      EnvelopeSummary summary =
          envelopesApi.createEnvelope(currentSession().accountId(), definition);
      String envelopeId = summary.getEnvelopeId();
      Recipients recipients = envelopesApi.listRecipients(currentSession().accountId(), envelopeId);
      List<SignerResult> signers =
          request.getSigners().stream()
              .map(
                  signer ->
                      new SignerResult(
                          signer.roleKey(),
                          findRecipientId(recipients, signer),
                          EsignSignerStatus.SENT))
              .toList();
      return new CreateEnvelopeResult(envelopeId, EsignEnvelopeStatus.SENT, Instant.now(), signers);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while creating envelope", e);
    }
  }

  @Override
  public void voidEnvelope(String externalEnvelopeId, String reason) {
    try {
      Envelope envelope = new Envelope();
      envelope.setStatus("voided");
      envelope.setVoidedReason(reason);
      envelopesApi().update(currentSession().accountId(), externalEnvelopeId, envelope);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while voiding envelope", e);
    }
  }

  @Override
  public void resendRecipients(String externalEnvelopeId, List<SignerRequest> signers) {
    if (signers == null || signers.isEmpty()) {
      return;
    }
    try {
      Recipients recipients = new Recipients();
      recipients.setSigners(signers.stream().map(this::toResendSigner).toList());
      EnvelopesApi api = envelopesApi();
      EnvelopesApi.UpdateRecipientsOptions options = api.new UpdateRecipientsOptions();
      options.setResendEnvelope("true");
      api.updateRecipients(currentSession().accountId(), externalEnvelopeId, recipients, options);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while resending recipients", e);
    }
  }

  @Override
  public List<EnvelopeStatusResult> listEnvelopeStatuses(List<String> externalEnvelopeIds) {
    if (externalEnvelopeIds == null || externalEnvelopeIds.isEmpty()) {
      return List.of();
    }
    try {
      EnvelopesApi api = envelopesApi();
      EnvelopeIdsRequest request = new EnvelopeIdsRequest();
      request.setEnvelopeIds(externalEnvelopeIds);
      EnvelopesApi.ListStatusOptions options = api.new ListStatusOptions();
      options.setEnvelopeIds(
          externalEnvelopeIds.stream()
              .filter(StringUtils::isNotBlank)
              .map(String::trim)
              .collect(java.util.stream.Collectors.joining(",")));
      EnvelopesInformation information =
          api.listStatus(currentSession().accountId(), request, options);
      if (information == null || information.getEnvelopes() == null) {
        return List.of();
      }
      return information.getEnvelopes().stream()
          .map(this::toEnvelopeStatusResult)
          .filter(Objects::nonNull)
          .toList();
    } catch (ApiException e) {
      throw new IllegalStateException(
          "DocuSign SDK call failed while listing envelope statuses", e);
    }
  }

  @Override
  public String createRecipientView(
      String externalEnvelopeId, SignerRequest signer, String locale) {
    return createRecipientView(envelopesApi(), externalEnvelopeId, signer, locale);
  }

  @Override
  public DownloadedDocument downloadCompletedDocument(
      String externalEnvelopeId, String fileName, byte[] sourceDocument) {
    try {
      byte[] content =
          envelopesApi().getDocument(currentSession().accountId(), externalEnvelopeId, "combined");
      return new DownloadedDocument(fileName, "application/pdf", content);
    } catch (Exception e) {
      throw new IllegalStateException(
          "DocuSign SDK call failed while downloading signed document", e);
    }
  }

  @Override
  public DownloadedCertificate downloadCertificate(
      String externalEnvelopeId, String envelopeSubject, List<SignerSnapshot> signers) {
    try {
      byte[] content =
          envelopesApi()
              .getDocument(currentSession().accountId(), externalEnvelopeId, "certificate");
      return new DownloadedCertificate(
          externalEnvelopeId + "-certificate.pdf", "application/pdf", content);
    } catch (Exception e) {
      throw new IllegalStateException("DocuSign SDK call failed while downloading certificate", e);
    }
  }

  private EnvelopesApi envelopesApi() {
    return new EnvelopesApi(apiClient());
  }

  private EnvelopeStatusResult toEnvelopeStatusResult(Envelope envelope) {
    if (envelope == null || StringUtils.isBlank(envelope.getEnvelopeId())) {
      return null;
    }
    EsignEnvelopeStatus status = toEnvelopeStatus(envelope.getStatus());
    if (status == null) {
      return null;
    }
    return new EnvelopeStatusResult(
        envelope.getEnvelopeId(),
        status,
        firstPresentTimestamp(
            envelope.getStatusChangedDateTime(),
            envelope.getCompletedDateTime(),
            envelope.getVoidedDateTime(),
            envelope.getDeliveredDateTime(),
            envelope.getSentDateTime()),
        StringUtils.trimToNull(envelope.getVoidedReason()));
  }

  private EsignEnvelopeStatus toEnvelopeStatus(String status) {
    if (StringUtils.isBlank(status)) {
      return null;
    }
    return switch (status.trim().toLowerCase(Locale.ROOT)) {
      case "created" -> EsignEnvelopeStatus.CREATED;
      case "sent" -> EsignEnvelopeStatus.SENT;
      case "delivered" -> EsignEnvelopeStatus.DELIVERED;
      case "declined" -> EsignEnvelopeStatus.DECLINED;
      case "completed" -> EsignEnvelopeStatus.COMPLETED;
      case "voided" -> EsignEnvelopeStatus.VOIDED;
      default -> null;
    };
  }

  private Instant firstPresentTimestamp(String... values) {
    for (String value : values) {
      if (StringUtils.isBlank(value)) {
        continue;
      }
      try {
        return Instant.parse(value.trim());
      } catch (DateTimeParseException ignored) {
        // Ignore provider timestamps that are not ISO-8601 parseable.
      }
    }
    return Instant.now();
  }

  private ApiClient apiClient() {
    DocuSignAuthProvider.AuthSession session = currentSession();
    ApiClient apiClient = new ApiClient(session.basePath());
    long expiresInSeconds =
        Math.max(60, session.expiresAt().getEpochSecond() - Instant.now().getEpochSecond());
    apiClient.setAccessToken(session.accessToken(), expiresInSeconds);
    return apiClient;
  }

  private DocuSignAuthProvider.AuthSession currentSession() {
    return authProvider.currentSession();
  }

  private Recipients toRecipients(CreateEnvelopeRequest request) {
    Recipients recipients = new Recipients();
    if (request.getDeliveryMode() == EsignDeliveryMode.IN_PERSON) {
      recipients.setInPersonSigners(
          request.getSigners().stream().map(this::toInPersonSigner).toList());
      return recipients;
    }
    recipients.setSigners(request.getSigners().stream().map(this::toSigner).toList());
    return recipients;
  }

  private Signer toSigner(SignerRequest signer) {
    Signer recipient = new Signer();
    recipient.setName(signer.fullName());
    if (signer.deliveryMethod() == EsignSignerDeliveryMethod.SMS) {
      recipient.setDeliveryMethod("SMS");
      recipient.setPhoneNumber(toRecipientPhoneNumber(signer.smsNumber()));
    } else {
      recipient.setEmail(signer.email());
    }
    recipient.setRecipientId(String.valueOf(signer.routingOrder()));
    recipient.setRoutingOrder(String.valueOf(signer.routingOrder()));
    if (signer.authMethod() == EsignAuthMethod.PASSCODE) {
      recipient.setAccessCode(signer.accessCode());
    }
    if (signer.authMethod() == EsignAuthMethod.SMS) {
      RecipientSMSAuthentication auth = new RecipientSMSAuthentication();
      auth.setSenderProvidedNumbers(List.of(signer.smsNumber()));
      recipient.setSmsAuthentication(auth);
    }
    recipient.setTabs(toTabs(signer.signatureAnchorText(), signer.dateAnchorText()));
    return recipient;
  }

  private Signer toResendSigner(SignerRequest signer) {
    Signer recipient = new Signer();
    recipient.setRecipientId(signer.providerRecipientId());
    recipient.setRoutingOrder(String.valueOf(signer.routingOrder()));
    recipient.setName(signer.fullName());
    if (signer.deliveryMethod() == EsignSignerDeliveryMethod.SMS) {
      recipient.setDeliveryMethod("SMS");
      recipient.setPhoneNumber(toRecipientPhoneNumber(signer.smsNumber()));
    } else {
      recipient.setEmail(signer.email());
    }
    return recipient;
  }

  private String findRecipientId(Recipients recipients, SignerRequest signer) {
    if (recipients == null) {
      return String.valueOf(signer.routingOrder());
    }
    if (signer.deliveryMode() == EsignDeliveryMode.IN_PERSON
        && recipients.getInPersonSigners() != null) {
      return recipients.getInPersonSigners().stream()
          .filter(candidate -> recipientMatches(candidate.getRoutingOrder(), signer.routingOrder()))
          .filter(candidate -> StringUtils.equals(candidate.getSignerName(), signer.fullName()))
          .map(InPersonSigner::getRecipientId)
          .filter(StringUtils::isNotBlank)
          .findFirst()
          .orElse(String.valueOf(signer.routingOrder()));
    }
    if (recipients.getSigners() != null) {
      return recipients.getSigners().stream()
          .filter(candidate -> recipientMatches(candidate.getRoutingOrder(), signer.routingOrder()))
          .filter(candidate -> StringUtils.equals(candidate.getName(), signer.fullName()))
          .filter(candidate -> remoteRecipientMatches(candidate, signer))
          .map(Signer::getRecipientId)
          .filter(StringUtils::isNotBlank)
          .findFirst()
          .orElse(String.valueOf(signer.routingOrder()));
    }
    return String.valueOf(signer.routingOrder());
  }

  private boolean recipientMatches(String recipientRoutingOrder, Integer signerRoutingOrder) {
    return StringUtils.equals(
        StringUtils.trimToNull(recipientRoutingOrder),
        signerRoutingOrder == null ? null : String.valueOf(signerRoutingOrder));
  }

  private boolean remoteRecipientMatches(Signer candidate, SignerRequest signer) {
    if (signer.deliveryMethod() == EsignSignerDeliveryMethod.SMS) {
      RecipientPhoneNumber phoneNumber = candidate.getPhoneNumber();
      if (phoneNumber == null) {
        return false;
      }
      String providerNumber =
          StringUtils.trimToNull(phoneNumber.getCountryCode()) == null
              ? StringUtils.trimToNull(phoneNumber.getNumber())
              : "+"
                  + phoneNumber.getCountryCode()
                  + StringUtils.defaultString(phoneNumber.getNumber());
      return StringUtils.equals(providerNumber, signer.smsNumber());
    }
    return StringUtils.equals(candidate.getEmail(), signer.email());
  }

  private RecipientPhoneNumber toRecipientPhoneNumber(String normalizedSmsNumber) {
    RecipientPhoneNumber phoneNumber = new RecipientPhoneNumber();
    String digits =
        normalizedSmsNumber.startsWith("+")
            ? normalizedSmsNumber.substring(1)
            : normalizedSmsNumber;
    phoneNumber.setCountryCode(digits.substring(0, 1));
    phoneNumber.setNumber(digits.substring(1));
    return phoneNumber;
  }

  private InPersonSigner toInPersonSigner(SignerRequest signer) {
    InPersonSigner recipient = new InPersonSigner();
    recipient.setSignerName(signer.fullName());
    recipient.setSignerEmail(signer.email());
    recipient.setHostEmail(resolvedHostEmail());
    recipient.setHostName(resolvedHostName());
    recipient.setRecipientId(String.valueOf(signer.routingOrder()));
    recipient.setRoutingOrder(String.valueOf(signer.routingOrder()));
    recipient.setTabs(toTabs(signer.signatureAnchorText(), signer.dateAnchorText()));
    return recipient;
  }

  private Notification toNotification(Integer reminderIntervalHours) {
    Reminders reminder = new Reminders();
    reminder.setReminderEnabled("true");
    reminder.setReminderDelay("0");
    reminder.setReminderFrequency(String.valueOf(Math.max(1, reminderIntervalHours / 24)));
    Notification notification = new Notification();
    notification.setUseAccountDefaults("false");
    notification.setReminders(reminder);
    return notification;
  }

  private Document toDocument(String fileName, byte[] bytes) {
    Document document = new Document();
    document.setDocumentBase64(Base64.getEncoder().encodeToString(bytes));
    document.setName(fileName);
    document.setFileExtension("pdf");
    document.setDocumentId("1");
    return document;
  }

  private SignHere toSignHereTab(String anchor) {
    SignHere tab = new SignHere();
    tab.setAnchorString(anchor);
    tab.setAnchorUnits("pixels");
    tab.setAnchorXOffset("0");
    tab.setAnchorYOffset("0");
    return tab;
  }

  private DateSigned toDateSignedTab(String anchor) {
    DateSigned tab = new DateSigned();
    tab.setAnchorString(anchor);
    tab.setAnchorUnits("pixels");
    tab.setAnchorXOffset("0");
    tab.setAnchorYOffset("0");
    return tab;
  }

  private Tabs toTabs(String signatureAnchorText, String dateAnchorText) {
    Tabs tabs = new Tabs();
    tabs.setSignHereTabs(List.of(toSignHereTab(signatureAnchorText)));
    if (StringUtils.isNotBlank(dateAnchorText)) {
      tabs.setDateSignedTabs(List.of(toDateSignedTab(dateAnchorText)));
    }
    return tabs;
  }

  private String createRecipientView(
      EnvelopesApi envelopesApi, String envelopeId, SignerRequest signer, String locale) {
    try {
      RecipientViewRequest request = toRecipientViewRequest(signer);
      ViewUrl viewUrl =
          envelopesApi.createRecipientView(currentSession().accountId(), envelopeId, request);
      return withLocale(viewUrl.getUrl(), locale);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while creating recipient view", e);
    }
  }

  String withLocale(String signingUrl, String locale) {
    String normalizedLocale = normalizeLocale(locale);
    if (normalizedLocale == null || StringUtils.isBlank(signingUrl)) {
      return signingUrl;
    }
    return UriComponentsBuilder.fromUriString(signingUrl)
        .replaceQueryParam("locale", normalizedLocale)
        .build(true)
        .toUriString();
  }

  private String normalizeLocale(String locale) {
    String trimmed = StringUtils.trimToNull(locale);
    if (trimmed == null) {
      return null;
    }
    return trimmed.replace('-', '_');
  }

  RecipientViewRequest toRecipientViewRequest(SignerRequest signer) {
    RecipientViewRequest request = new RecipientViewRequest();
    request.setAuthenticationMethod("none");
    request.setRecipientId(signer.providerRecipientId());
    request.setReturnUrl(properties.getReturnUrl());
    if (signer.deliveryMode() == EsignDeliveryMode.IN_PERSON) {
      request.setEmail(resolvedHostEmail());
      request.setUserName(resolvedHostName());
      return request;
    }
    request.setClientUserId(signer.roleKey());
    request.setEmail(signer.email());
    request.setUserName(signer.fullName());
    return request;
  }

  private String resolvedHostEmail() {
    String configured = StringUtils.trimToNull(properties.getHostEmail());
    if (configured != null) {
      return configured;
    }
    String sessionEmail = StringUtils.trimToNull(currentSession().email());
    if (sessionEmail != null) {
      return sessionEmail;
    }
    throw new IllegalStateException(
        "In-person signing requires a valid DocuSign host email. "
            + "Set DOCUSIGN_HOST_EMAIL or ensure the JWT userinfo response includes an email.");
  }

  private String resolvedHostName() {
    String configured = StringUtils.trimToNull(properties.getHostName());
    if (configured != null) {
      return configured;
    }
    String sessionName = StringUtils.trimToNull(currentSession().name());
    if (sessionName != null) {
      return sessionName;
    }
    throw new IllegalStateException(
        "In-person signing requires a valid DocuSign host name. "
            + "Set DOCUSIGN_HOST_NAME or ensure the JWT userinfo response includes a name.");
  }
}
