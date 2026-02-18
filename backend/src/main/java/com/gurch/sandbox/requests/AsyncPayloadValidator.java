package com.gurch.sandbox.requests;

/**
 * Contract for asynchronous request payload validation executed from workflow task handlers.
 *
 * @param <T> typed payload model expected by this validator
 */
public interface AsyncPayloadValidator<T> {
  /**
   * Stable validator identifier.
   *
   * @return validator id
   */
  String id();

  /**
   * Target payload type used for JSON deserialization before validation.
   *
   * @return typed payload class
   */
  Class<T> payloadType();

  /**
   * Executes asynchronous business validation.
   *
   * @param payload typed payload
   * @param context request metadata and payload context
   * @return async validation result
   */
  AsyncPayloadValidationResult validate(T payload, AsyncPayloadValidationContext context);
}
