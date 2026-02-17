package com.gurch.sandbox.query;

/**
 * Represents a single condition in a WHERE clause.
 *
 * @param column the column name
 * @param operator the operator
 * @param value the value
 */
public record WhereClause(String column, Operator operator, Object value) {
  /**
   * Factory method to create a new where clause.
   *
   * @param column the column name
   * @param operator the operator
   * @param value the value
   * @return a new where clause instance
   */
  public static WhereClause create(String column, Operator operator, Object value) {
    return new WhereClause(column, operator, value);
  }
}
