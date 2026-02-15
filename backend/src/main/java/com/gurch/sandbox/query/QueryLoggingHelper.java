package com.gurch.sandbox.query;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class QueryLoggingHelper {
  private QueryLoggingHelper() {
    // Utility class.
  }

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
