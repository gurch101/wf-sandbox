package com.gurch.sandbox.esign;

/** Service API for starting and tracking request-linked e-sign envelopes. */
public interface EsignApi {

  /**
   * Starts a new envelope using a pre-generated PDF document and recipient details.
   *
   * @param request envelope start payload
   * @return envelope start projection
   */
  StartEsignResponse startRemote(StartRemoteEsignRequest request);

  /**
   * Starts a new embedded/in-person envelope using a pre-generated PDF document.
   *
   * @param request envelope start payload
   * @return envelope start projection
   */
  StartEsignResponse startEmbedded(StartEmbeddedEsignRequest request);

  /**
   * Voids an existing envelope.
   *
   * @param request void command payload
   */
  void voidEnvelope(VoidEsignEnvelopeRequest request);

  /**
   * Triggers resend on an existing envelope.
   *
   * @param request resend command payload
   */
  void resendEnvelope(ResendEsignEnvelopeRequest request);

  /**
   * Downloads combined signed document and completion certificate.
   *
   * @param request download command payload
   * @return artifact bytes
   */
  DownloadEsignArtifactsResponse downloadArtifacts(DownloadEsignArtifactsRequest request);

  /**
   * Handles DocuSign webhook envelope status updates.
   *
   * @param update webhook payload
   * @param signatureHeader DocuSign signature header value
   */
  void handleWebhookStatusUpdate(EsignWebhookStatusUpdate update, String signatureHeader);
}
