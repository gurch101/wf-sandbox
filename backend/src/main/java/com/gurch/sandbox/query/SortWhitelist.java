package com.gurch.sandbox.query;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SortWhitelist {
  private final Map<String, String> mappings = new LinkedHashMap<>();

  private SortWhitelist() {
    // Use factory method.
  }

  public static SortWhitelist create() {
    return new SortWhitelist();
  }

  public SortWhitelist allow(String field, String expression) {
    mappings.put(field, expression);
    return this;
  }

  public String resolve(String field) {
    return mappings.get(field);
  }
}
