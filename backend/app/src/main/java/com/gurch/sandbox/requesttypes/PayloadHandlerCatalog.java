package com.gurch.sandbox.requesttypes;

/** Read-only catalog for registered payload handlers. */
public interface PayloadHandlerCatalog {
  /**
   * Returns true when a payload handler with the provided id is registered.
   *
   * @param handlerId stable handler identifier
   * @return whether the handler exists
   */
  boolean exists(String handlerId);

  /**
   * Returns the typed payload class for a registered handler.
   *
   * @param handlerId stable handler identifier
   * @return typed payload class
   */
  Class<?> payloadType(String handlerId);
}
