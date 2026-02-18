package com.gurch.sandbox.requests;

/**
 * Contract for synchronous payload validation by request type/version handler mapping.
 *
 * <p>Implementations may live in other domain modules and be discovered as Spring beans.
 *
 * @param <T> typed payload model expected by this handler
 */
public interface PreWorkflowPayloadValidator<T> {
  /**
   * Stable handler identifier referenced by request type versions.
   *
   * @return handler id
   */
  String id();

  /**
   * Target payload type used for JSON deserialization before validation.
   *
   * @return typed payload class
   */
  Class<T> payloadType();

  /**
   * Validates request payload synchronously before request persistence/workflow start.
   *
   * @param payload request payload
   */
  void validate(T payload);
}
