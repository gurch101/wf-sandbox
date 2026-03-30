package com.gurch.sandbox.esign;

import com.gurch.sandbox.esign.dto.EsignReconcileResponse;

/** Reconciles stored e-sign envelopes against current provider state. */
public interface EsignReconciliationApi {

  /**
   * Reconciles active local envelopes against the provider's current state.
   *
   * @return summary of the reconciliation run
   */
  EsignReconcileResponse reconcileActiveEnvelopes();
}
