package com.gurch.sandbox.esign;

import com.gurch.sandbox.esign.dto.EsignWebhookRequest;

/** Applies inbound provider webhook updates to stored e-sign state. */
public interface EsignWebhookApi {

  /**
   * Applies the mapped provider webhook payload to local envelope and signer state.
   *
   * @param request normalized webhook update request
   */
  void handleWebhook(EsignWebhookRequest request);
}
