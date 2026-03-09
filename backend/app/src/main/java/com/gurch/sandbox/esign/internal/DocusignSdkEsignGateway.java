package com.gurch.sandbox.esign.internal;

import com.docusign.esign.api.EnvelopesApi;
import com.docusign.esign.client.ApiClient;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.model.CertifiedDelivery;
import com.docusign.esign.model.CustomFields;
import com.docusign.esign.model.DateSigned;
import com.docusign.esign.model.Document;
import com.docusign.esign.model.Envelope;
import com.docusign.esign.model.EnvelopeDefinition;
import com.docusign.esign.model.EnvelopeSummary;
import com.docusign.esign.model.EnvelopesInformation;
import com.docusign.esign.model.RecipientAdditionalNotification;
import com.docusign.esign.model.RecipientPhoneNumber;
import com.docusign.esign.model.RecipientViewRequest;
import com.docusign.esign.model.Recipients;
import com.docusign.esign.model.SignHere;
import com.docusign.esign.model.Signer;
import com.docusign.esign.model.Tabs;
import com.docusign.esign.model.TextCustomField;
import com.gurch.sandbox.esign.EsignRemoteDeliveryMethod;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
class DocusignSdkEsignGateway implements DocusignEsignGateway {

  @Override
  public DocusignEnvelopeResult createEnvelope(
      TenantDocusignConfigEntity config, DocusignEnvelopeRequest request) {
    try {
      EnvelopesApi envelopesApi = envelopesApi(config);
      Optional<String> existingEnvelopeId =
          findEnvelopeIdByIdempotencyKey(envelopesApi, config, request.getIdempotencyKey());
      if (existingEnvelopeId.isPresent()) {
        String envelopeId = existingEnvelopeId.get();
        String recipientViewUrl =
            createRecipientViewIfEmbedded(envelopesApi, config, request, envelopeId);
        return new DocusignEnvelopeResult(envelopeId, recipientViewUrl);
      }

      EnvelopeDefinition envelopeDefinition = new EnvelopeDefinition();
      envelopeDefinition.setEmailSubject(
          request.getEmailSubject() == null ? "Please sign" : request.getEmailSubject());
      envelopeDefinition.setEmailBlurb(request.getEmailMessage());
      envelopeDefinition.setStatus("sent");
      if (hasText(request.getIdempotencyKey())) {
        envelopeDefinition.setTransactionId(request.getIdempotencyKey());
        envelopeDefinition.setCustomFields(
            new CustomFields()
                .addTextCustomFieldsItem(
                    new TextCustomField()
                        .name("idempotencyKey")
                        .required("false")
                        .show("false")
                        .value(request.getIdempotencyKey())));
      }
      envelopeDefinition.setDocuments(
          List.of(
              new Document()
                  .documentId("1")
                  .name(
                      request.getDocumentName() == null
                          ? "generated-document-bundle.pdf"
                          : request.getDocumentName())
                  .fileExtension("pdf")
                  .documentBase64(Base64.getEncoder().encodeToString(request.getDocumentPdf()))));
      envelopeDefinition.setRecipients(toRecipients(request));

      EnvelopeSummary summary =
          envelopesApi.createEnvelope(config.getAccountId(), envelopeDefinition);
      String envelopeId = summary.getEnvelopeId();
      String recipientViewUrl =
          createRecipientViewIfEmbedded(envelopesApi, config, request, envelopeId);
      return new DocusignEnvelopeResult(envelopeId, recipientViewUrl);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign envelope creation failed", e);
    }
  }

  @Override
  public void voidEnvelope(TenantDocusignConfigEntity config, String envelopeId, String reason) {
    try {
      EnvelopesApi envelopesApi = envelopesApi(config);
      Envelope envelope = new Envelope().status("voided").voidedReason(reason);
      envelopesApi.update(config.getAccountId(), envelopeId, envelope);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign envelope void failed", e);
    }
  }

  @Override
  public void resendEnvelope(TenantDocusignConfigEntity config, String envelopeId) {
    try {
      EnvelopesApi envelopesApi = envelopesApi(config);
      EnvelopesApi.UpdateOptions options = envelopesApi.new UpdateOptions();
      options.setResendEnvelope("true");
      Envelope envelope = new Envelope().status("sent");
      envelopesApi.update(config.getAccountId(), envelopeId, envelope, options);
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign envelope resend failed", e);
    }
  }

  @Override
  public DocusignEnvelopeArtifacts downloadArtifacts(
      TenantDocusignConfigEntity config, String envelopeId) {
    try {
      EnvelopesApi envelopesApi = envelopesApi(config);
      byte[] signedDocument =
          envelopesApi.getDocument(config.getAccountId(), envelopeId, "combined");
      byte[] certificate =
          envelopesApi.getDocument(config.getAccountId(), envelopeId, "certificate");
      return DocusignEnvelopeArtifacts.builder()
          .signedDocumentPdf(signedDocument)
          .certificatePdf(certificate)
          .build();
    } catch (ApiException e) {
      throw new IllegalStateException("DocuSign artifact download failed", e);
    }
  }

  private Recipients toRecipients(DocusignEnvelopeRequest request) {
    Recipients recipients = new Recipients();
    recipients.setSigners(request.getSigners().stream().map(this::toSigner).toList());
    recipients.setCertifiedDeliveries(
        request.getCcRecipients().stream().map(this::toCertifiedDelivery).toList());
    return recipients;
  }

  private Signer toSigner(DocusignEnvelopeRequest.SignerRecipient signerRequest) {
    SignHere signHere = new SignHere().anchorString(signerRequest.getSignAnchor());

    Tabs tabs = new Tabs().signHereTabs(List.of(signHere));
    if (signerRequest.getDateAnchor() != null) {
      tabs.dateSignedTabs(List.of(new DateSigned().anchorString(signerRequest.getDateAnchor())));
    }

    Signer signer =
        new Signer()
            .recipientId(signerRequest.getRecipientId())
            .name(signerRequest.getName())
            .email(signerRequest.getEmail())
            .routingOrder(String.valueOf(signerRequest.getRoutingOrder()))
            .clientUserId(signerRequest.getRecipientId())
            .tabs(tabs);
    if (hasText(signerRequest.getAccessCode())) {
      signer.accessCode(signerRequest.getAccessCode());
    }

    EsignRemoteDeliveryMethod remoteDeliveryMethod =
        signerRequest.getRemoteDeliveryMethod() == null
            ? EsignRemoteDeliveryMethod.EMAIL
            : signerRequest.getRemoteDeliveryMethod();
    if (remoteDeliveryMethod == EsignRemoteDeliveryMethod.SMS
        && hasText(signerRequest.getSmsCountryCode())
        && hasText(signerRequest.getSmsNumber())) {
      signer.deliveryMethod("SMS");
      signer.phoneNumber(
          new RecipientPhoneNumber()
              .countryCode(signerRequest.getSmsCountryCode())
              .number(signerRequest.getSmsNumber()));
    } else if (hasText(signerRequest.getSmsCountryCode())
        && hasText(signerRequest.getSmsNumber())) {
      signer.addAdditionalNotificationsItem(
          new RecipientAdditionalNotification()
              .secondaryDeliveryMethod("SMS")
              .phoneNumber(
                  new RecipientPhoneNumber()
                      .countryCode(signerRequest.getSmsCountryCode())
                      .number(signerRequest.getSmsNumber())));
    }
    return signer;
  }

  private CertifiedDelivery toCertifiedDelivery(DocusignEnvelopeRequest.CcRecipient cc) {
    return new CertifiedDelivery()
        .recipientId(cc.getRecipientId())
        .name(cc.getName())
        .email(cc.getEmail())
        .routingOrder(String.valueOf(cc.getRoutingOrder()));
  }

  private String createRecipientViewIfEmbedded(
      EnvelopesApi envelopesApi,
      TenantDocusignConfigEntity config,
      DocusignEnvelopeRequest request,
      String envelopeId)
      throws ApiException {
    if (request.getSignatureMode() != com.gurch.sandbox.esign.EsignSignatureMode.EMBEDDED) {
      return null;
    }
    DocusignEnvelopeRequest.SignerRecipient firstSigner = request.getSigners().getFirst();
    RecipientViewRequest viewRequest =
        new RecipientViewRequest()
            .returnUrl("https://app.local/esign/return")
            .authenticationMethod("none")
            .userName(firstSigner.getName())
            .email(firstSigner.getEmail())
            .clientUserId(firstSigner.getRecipientId());
    return envelopesApi
        .createRecipientView(config.getAccountId(), envelopeId, viewRequest)
        .getUrl();
  }

  private EnvelopesApi envelopesApi(TenantDocusignConfigEntity config) {
    ApiClient apiClient = new ApiClient(config.getBasePath());
    apiClient.addDefaultHeader("Authorization", "Bearer " + config.getAuthToken());
    return new EnvelopesApi(apiClient);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }

  private Optional<String> findEnvelopeIdByIdempotencyKey(
      EnvelopesApi envelopesApi, TenantDocusignConfigEntity config, String idempotencyKey)
      throws ApiException {
    if (!hasText(idempotencyKey)) {
      return Optional.empty();
    }

    EnvelopesApi.ListStatusChangesOptions options = envelopesApi.new ListStatusChangesOptions();
    options.setFromDate(OffsetDateTime.now(ZoneOffset.UTC).minusDays(30).toString());
    options.setTransactionIds(idempotencyKey);
    options.setCount("1");
    EnvelopesInformation information =
        envelopesApi.listStatusChanges(config.getAccountId(), options);
    if (information == null || information.getEnvelopes() == null) {
      return Optional.empty();
    }
    return information.getEnvelopes().stream()
        .map(com.docusign.esign.model.Envelope::getEnvelopeId)
        .filter(DocusignSdkEsignGateway::hasText)
        .findFirst();
  }
}
