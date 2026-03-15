package com.gurch.sandbox.requesttypes;

/** Backend-supported escalation handlers available to all request types. */
public enum EscalationHandler {
  OPS_REROUTE,
  SLA_BREACH_MANAGER_ESCALATION,
  NOTIFY_REQUEST_OWNER
}
