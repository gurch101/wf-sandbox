package com.gurch.sandbox.esign.internal;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.model.CarbonCopy;
import com.docusign.esign.model.DateSigned;
import com.docusign.esign.model.Document;
import com.docusign.esign.model.Envelope;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.EnvelopeSummary;
import com.docusign.esign.model.Notification;
import com.docusign.esign.model.RecipientSMSAuthentication;
import com.docusign.esign.model.Recipients;
import com.docusign.esign.model.Reminders;
import com.docusign.esign.model.SignHere;
import com.docusign.esign.model.Signer;
import com.docusign.esign.model.Tabs;
import com.docusign.esign.model.ViewUrl;
import com.gurch.sandbox.esign.EsignAuthMethod;
import com.gurch.sandbox.esign.EsignDeliveryMode;
import com.gurch.sandbox.esign.EsignEnvelopeStatus;
import com.gurch.sandbox.esign.EsignSignerStatus;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

public interface DocuSignGateway {
  CreateEnvelopeResult createEnvelope(CreateEnvelopeRequest request);

  void voidEnvelope(String externalEnvelopeId, String reason);

  void deleteEnvelope(String externalEnvelopeId);

  String createRecipientView(String externalEnvelopeId, SignerRequest signer);

  DownloadedDocument downloadCompletedDocument(
      String externalEnvelopeId, String fileName, byte[] sourceDocument);

  DownloadedCertificate downloadCertificate(
      String externalEnvelopeId, String envelopeSubject, List<SignerSnapshot> signers);

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

    public CreateEnvelopeRequest(
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
      this.documentBytes = documentBytes == null ? new byte[0] : Arrays.copyOf(documentBytes, documentBytes.length);
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
      String phoneNumber,
      EsignAuthMethod authMethod,
      String accessCode,
      String smsNumber,
      String signatureAnchorText,
      String dateAnchorText,
      Integer routingOrder) {}

  record CreateEnvelopeResult(
      String externalEnvelopeId,
      EsignEnvelopeStatus envelopeStatus,
      java.time.Instant providerTimestamp,
      List<SignerResult> signers) {}

  record SignerResult(
      String roleKey,
      String providerRecipientId,
      EsignSignerStatus status) {}

  record SignerSnapshot(String fullName, String email, String roleKey, java.time.Instant completedAt) {}

  @Value
  class DownloadedCertificate {
    String fileName;
    String mimeType;
    byte[] content;

    public DownloadedCertificate(String fileName, String mimeType, byte[] content) {
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

    public DownloadedDocument(String fileName, String mimeType, byte[] content) {
      this.fileName = fileName;
      this.mimeType = mimeType;
      this.content = content == null ? new byte[0] : Arrays.copyOf(content, content.length);
    }

    public byte[] getContent() {
      return Arrays.copyOf(content, content.length);
    }
  }
}

@Data
@Component
@ConfigurationProperties(prefix = "docusign")
class DocuSignProperties {
  private String basePath;
  private String accountId;
  private String accessToken;
  private String webhookHmacSecret;
  private String returnUrl = "https://example.com/docusign/return";
  private String hostEmail = "system@sandbox.local";
  private String hostName = "Sandbox Host";
}

@Component
@RequiredArgsConstructor
class DocuSignSdkGateway implements DocuSignGateway {

  private final DocuSignProperties properties;

  @Override
  public CreateEnvelopeResult createEnvelope(CreateEnvelopeRequest request) {
    try {
      EnvelopesApi envelopesApi = envelopesApi();
      EnvelopeDefinition definition = new EnvelopeDefinition();
      definition.setEmailSubject(request.getSubject());
      definition.setEmailBlurb(StringUtils.trimToNull(request.getMessage()));
      definition.setStatus("sent");
      definition.setDocuments(List.of(toDocument(request.getFileName(), request.getDocumentBytes())));
      definition.setRecipients(toRecipients(request));
      if (request.getDeliveryMode() == EsignDeliveryMode.REMOTE && request.isRemindersEnabled()) {
        definition.setNotification(toNotification(request.getReminderIntervalHours()));
      }

      EnvelopeSummary summary = envelopesApi.createEnvelope(properties.getAccountId(), definition);
      String envelopeId = summary.getEnvelopeId();
      List<SignerResult> signers =
          request.getSigners().stream()
              .map(
                  signer ->
                      new SignerResult(
                          signer.roleKey(),
                          String.valueOf(signer.routingOrder()),
                          EsignSignerStatus.SENT))
              .toList();
      return new CreateEnvelopeResult(
          envelopeId, EsignEnvelopeStatus.SENT, java.time.Instant.now(), signers);
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
      envelopesApi().update(properties.getAccountId(), externalEnvelopeId, envelope);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while voiding envelope", e);
    }
  }

  @Override
  public void deleteEnvelope(String externalEnvelopeId) {
    // DocuSign generally does not support deleting sent envelopes. Local record deletion handles this.
  }

  @Override
  public String createRecipientView(String externalEnvelopeId, SignerRequest signer) {
    return createRecipientView(envelopesApi(), externalEnvelopeId, signer);
  }

  @Override
  public DownloadedDocument downloadCompletedDocument(
      String externalEnvelopeId, String fileName, byte[] sourceDocument) {
    try {
      byte[] content =
          envelopesApi().getDocument(properties.getAccountId(), externalEnvelopeId, "combined");
      return new DownloadedDocument(fileName, "application/pdf", content);
    } catch (Exception e) {
      throw new IllegalStateException("DocuSign SDK call failed while downloading signed document", e);
    }
  }

  @Override
  public DownloadedCertificate downloadCertificate(
      String externalEnvelopeId, String envelopeSubject, List<SignerSnapshot> signers) {
    try {
      byte[] content =
          envelopesApi().getDocument(properties.getAccountId(), externalEnvelopeId, "certificate");
      return new DownloadedCertificate(
          externalEnvelopeId + "-certificate.pdf", "application/pdf", content);
    } catch (Exception e) {
      throw new IllegalStateException("DocuSign SDK call failed while downloading certificate", e);
    }
  }

  private EnvelopesApi envelopesApi() {
    return new EnvelopesApi(apiClient());
  }

  private ApiClient apiClient() {
    ApiClient apiClient = new ApiClient(properties.getBasePath());
    apiClient.addDefaultHeader("Authorization", "Bearer " + properties.getAccessToken());
    return apiClient;
  }

  private Recipients toRecipients(CreateEnvelopeRequest request) {
    List<Signer> signers =
        request.getSigners().stream()
            .map(signer -> toSigner(signer, request.getDeliveryMode()))
            .toList();
    Recipients recipients = new Recipients();
    recipients.setSigners(signers);
    return recipients;
  }

  private Signer toSigner(SignerRequest signer, EsignDeliveryMode deliveryMode) {
    Signer recipient = new Signer();
    recipient.setName(signer.fullName());
    recipient.setEmail(signer.email());
    recipient.setRecipientId(String.valueOf(signer.routingOrder()));
    recipient.setRoutingOrder(String.valueOf(signer.routingOrder()));
    if (deliveryMode == EsignDeliveryMode.IN_PERSON) {
      recipient.setClientUserId(signer.roleKey());
    }
    if (signer.authMethod() == EsignAuthMethod.PASSCODE) {
      recipient.setAccessCode(signer.accessCode());
    }
    if (signer.authMethod() == EsignAuthMethod.SMS) {
      RecipientSMSAuthentication auth = new RecipientSMSAuthentication();
      auth.setSenderProvidedNumbers(List.of(signer.smsNumber()));
      recipient.setSmsAuthentication(auth);
    }
    Tabs tabs = new Tabs();
    tabs.setSignHereTabs(List.of(toSignHereTab(signer.signatureAnchorText())));
    if (StringUtils.isNotBlank(signer.dateAnchorText())) {
      tabs.setDateSignedTabs(List.of(toDateSignedTab(signer.dateAnchorText())));
    }
    recipient.setTabs(tabs);
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

  private String createRecipientView(
      EnvelopesApi envelopesApi, String envelopeId, SignerRequest signer) {
    try {
      com.docusign.esign.model.RecipientViewRequest request =
          new com.docusign.esign.model.RecipientViewRequest();
      request.setAuthenticationMethod("none");
      request.setClientUserId(signer.roleKey());
      request.setEmail(signer.email());
      request.setReturnUrl(properties.getReturnUrl());
      request.setUserName(signer.fullName());
      ViewUrl viewUrl =
          envelopesApi.createRecipientView(properties.getAccountId(), envelopeId, request);
      return viewUrl.getUrl();
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign SDK call failed while creating recipient view", e);
    }
  }
}
