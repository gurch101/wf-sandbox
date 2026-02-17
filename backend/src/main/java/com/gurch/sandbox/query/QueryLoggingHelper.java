package com.gurch.sandbox.query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Helper class for logging built queries with parameter redaction. */
public final class QueryLoggingHelper {
  private QueryLoggingHelper() {
    // Utility class.
  }

  /**
   * Formats a query for logging, optionally redacting sensitive parameters.
   *
   * @param queryId a unique identifier for the query
   * @param query the built query to format
   * @param redactKeys the keys of parameters to redact
   * @return a formatted string suitable for logging
   */
  public static String format(String queryId, BuiltQuery query, Set<String> redactKeys) {
    Map<String, Object> redacted = new LinkedHashMap<>();
    query
        .params()
        .forEach(
            (key, value) -> {
              Object renderedValue = redactKeys.contains(key) ? "***" : value;
              redacted.put(key, renderedValue);
            });
    return "queryId="
        + queryId
        + " sql="
        + query.sql()
        + " paramKeys="
        + query.params().keySet()
        + " redactedParams="
        + redacted;
  }
}
