package com.gurch.sandbox.query;

/** Supported SQL join types. */
public enum JoinType {
  INNER("INNER JOIN"),
  LEFT("LEFT JOIN"),
  RIGHT("RIGHT JOIN"),
  FULL("FULL JOIN");

  private final String sql;

  JoinType(String sql) {
    this.sql = sql;
  }

  /**
   * Returns the SQL fragment for this join type.
   *
   * @return the SQL string
   */
  public String sql() {
    return sql;
  }
}
