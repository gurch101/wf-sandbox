package com.gurch.sandbox.persistence;

import org.springframework.dao.DataIntegrityViolationException;

/** Utility helpers for mapping persistence-layer exceptions. */
public final class PersistenceExceptionUtils {
  private PersistenceExceptionUtils() {}

  /** Builds a flattened message chain for nested persistence exceptions. */
  public static String fullMessage(Throwable throwable) {
    StringBuilder builder = new StringBuilder();
    Throwable current = throwable;
    while (current != null) {
      if (current.getMessage() != null) {
        builder.append(current.getMessage()).append('\n');
      }
      if (current instanceof DataIntegrityViolationException
          && current.getCause() != null
          && current.getCause().getMessage() != null) {
        builder.append(current.getCause().getMessage()).append('\n');
      }
      current = current.getCause();
    }
    return builder.toString();
  }
}
