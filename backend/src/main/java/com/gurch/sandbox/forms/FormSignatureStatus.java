package com.gurch.sandbox.forms;

/** Signature lifecycle state for future e-sign integrations like DocuSign. */
public enum FormSignatureStatus {
  NOT_REQUESTED,
  REQUESTED,
  COMPLETED,
  DECLINED,
  FAILED
}
