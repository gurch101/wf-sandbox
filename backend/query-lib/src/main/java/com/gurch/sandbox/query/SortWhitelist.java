package com.gurch.sandbox.query;

import java.util.LinkedHashMap;
import java.util.Map;

/** A whitelist of allowed sort fields to prevent SQL injection in dynamic ORDER BY clauses. */
public final class SortWhitelist {
  private final Map<String, String> mappings = new LinkedHashMap<>();

  private SortWhitelist() {
    // Use factory method.
  }

  /**
   * Creates a new empty whitelist.
   *
   * @return a new whitelist instance
   */
  public static SortWhitelist create() {
    return new SortWhitelist();
  }

  /**
   * Adds an allowed sort field and its corresponding SQL expression.
   *
   * @param field the field name from the client
   * @param expression the safe SQL expression to use
   * @return this whitelist
   */
  public SortWhitelist allow(String field, String expression) {
    mappings.put(field, expression);
    return this;
  }

  /**
   * Resolves a client field name to its SQL expression.
   *
   * @param field the field name from the client
   * @return the SQL expression if allowed, or null if not
   */
  public String resolve(String field) {
    return mappings.get(field);
  }
}
