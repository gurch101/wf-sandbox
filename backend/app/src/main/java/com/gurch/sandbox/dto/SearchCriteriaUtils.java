package com.gurch.sandbox.dto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Shared helpers for criteria normalization. */
public final class SearchCriteriaUtils {
  private SearchCriteriaUtils() {}

  /**
   * Converts optional text into an uppercase SQL LIKE pattern.
   *
   * @param value raw filter value
   * @return uppercase wildcard pattern or null when blank
   */
  public static String toUpperLikePattern(String value) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }

  /**
   * Converts optional text into an uppercase SQL prefix-LIKE pattern.
   *
   * @param value raw filter value
   * @return uppercase prefix wildcard pattern or null when blank
   */
  public static String toUpperStartsWithPattern(String value) {
    return Optional.ofNullable(value)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> s.toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }

  /**
   * Normalizes optional string list input for SQL IN usage.
   *
   * @param values raw values
   * @return trimmed non-blank values or null when empty
   */
  public static List<String> normalizeStringList(List<String> values) {
    List<String> normalized = new ArrayList<>();
    Optional.ofNullable(values).stream()
        .flatMap(List::stream)
        .filter(s -> s != null)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .forEach(normalized::add);
    return normalized.isEmpty() ? null : normalized;
  }

  /**
   * Normalizes optional string list input and uppercases values.
   *
   * @param values raw values
   * @return trimmed uppercase values or null when empty
   */
  public static List<String> normalizeUppercaseStringList(List<String> values) {
    List<String> normalized = normalizeStringList(values);
    return normalized == null
        ? null
        : normalized.stream().map(s -> s.toUpperCase(Locale.ROOT)).toList();
  }
}
