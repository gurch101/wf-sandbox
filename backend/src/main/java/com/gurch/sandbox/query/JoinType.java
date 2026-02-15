package com.gurch.sandbox.query;

public enum JoinType {
  INNER("INNER JOIN"),
  LEFT("LEFT JOIN"),
  RIGHT("RIGHT JOIN"),
  FULL("FULL JOIN");

  private final String sql;

  JoinType(String sql) {
    this.sql = sql;
  }

  public String sql() {
    return sql;
  }
}
