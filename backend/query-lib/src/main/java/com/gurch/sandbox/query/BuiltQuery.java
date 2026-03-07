package com.gurch.sandbox.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a built SQL query with its associated named parameters.
 *
 * @param sql the SQL string
 * @param params the named parameters
 */
public record BuiltQuery(String sql, Map<String, Object> params) {
  /** Compact constructor to ensure params map is unmodifiable. */
  public BuiltQuery {
    params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
  }
}
