package com.gurch.sandbox.query;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record BuiltQuery(String sql, Map<String, Object> params) {
  public BuiltQuery {
    params = Collections.unmodifiableMap(new LinkedHashMap<>(params));
  }
}
