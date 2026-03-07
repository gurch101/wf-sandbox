package com.gurch.sandbox.requests;

/**
 * Result returned by async payload validators.
 *
 * @param passed whether async validation passed
 * @param reason optional failure reason when validation fails
 */
public record AsyncPayloadValidationResult(boolean passed, String reason) {
  /**
   * Returns a passing async validation result.
   *
   * @return successful validation result
   */
  public static AsyncPayloadValidationResult success() {
    return new AsyncPayloadValidationResult(true, null);
  }

  /**
   * Returns a failing async validation result.
   *
   * @param reason failure reason
   * @return failed validation result
   */
  public static AsyncPayloadValidationResult failed(String reason) {
    return new AsyncPayloadValidationResult(false, reason);
  }
}
