package com.gurch.sandbox.idempotency.internal;

/** Represents the status of an idempotent request. */
public enum IdempotencyStatus {
  /** The request is currently being processed. */
  PROCESSING,
  /** The request has been successfully completed. */
  COMPLETED
}
