package com.gurch.sandbox.esign;

/** Lifecycle states for one signer within an envelope. */
public enum EsignSignerStatus {
  CREATED,
  SENT,
  DELIVERED,
  COMPLETED,
  DECLINED,
  VOIDED
}
