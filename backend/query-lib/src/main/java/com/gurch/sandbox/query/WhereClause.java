package com.gurch.sandbox.query;

import java.util.LinkedHashMap;
import java.util.Map;

/** Represents a single condition in a grouped OR WHERE clause. */
public final class WhereClause {
  private final String column;
  private final Operator operator;
  private final Object value;
  private final String rawFragment;
  private final Map<String, Object> rawParams;

  private WhereClause(
      String column,
      Operator operator,
      Object value,
      String rawFragment,
      Map<String, Object> rawParams) {
    this.column = column;
    this.operator = operator;
    this.value = value;
    this.rawFragment = rawFragment;
    this.rawParams = rawParams == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(rawParams));
  }

  /**
   * Creates a typed where clause using column/operator/value semantics.
   *
   * @param column the column name
   * @param operator the operator
   * @param value the value
   * @return a new where clause instance
   */
  public static WhereClause create(String column, Operator operator, Object value) {
    return new WhereClause(column, operator, value, null, Map.of());
  }

  /**
   * Creates a raw SQL fragment clause with named parameters.
   *
   * @param fragment the raw SQL fragment
   * @param params the fragment parameters
   * @return a new where clause instance
   */
  public static WhereClause raw(String fragment, Map<String, Object> params) {
    return new WhereClause(null, null, null, fragment, params);
  }

  /** Returns whether this clause wraps a raw SQL fragment instead of a typed predicate. */
  public boolean isRaw() {
    return rawFragment != null;
  }

  /** Returns the column for typed clauses, or {@code null} for raw clauses. */
  public String column() {
    return column;
  }

  /** Returns the operator for typed clauses, or {@code null} for raw clauses. */
  public Operator operator() {
    return operator;
  }

  /** Returns the comparison value for typed clauses, or {@code null} for raw clauses. */
  public Object value() {
    return value;
  }

  /** Returns the raw SQL fragment for raw clauses, or {@code null} for typed clauses. */
  public String rawFragment() {
    return rawFragment;
  }

  /** Returns the named parameters associated with a raw SQL fragment. */
  public Map<String, Object> rawParams() {
    return rawParams;
  }
}
