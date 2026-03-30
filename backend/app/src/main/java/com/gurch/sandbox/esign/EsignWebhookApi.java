package com.gurch.sandbox.esign;

/** Applies inbound provider webhook updates to stored e-sign state. */
public interface EsignWebhookApi {

  /**
   * Applies the mapped provider webhook payload to local envelope and signer state.
   *
   * @param request normalized webhook update request
   */
  void handleWebhook(EsignWebhookRequest request);
}
