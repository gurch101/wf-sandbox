package com.gurch.sandbox.esign;

/** Lifecycle states for an e-sign envelope. */
public enum EsignEnvelopeStatus {
  CREATED,
  SENT,
  DELIVERED,
  COMPLETED,
  VOIDED,
  DELETED
}
