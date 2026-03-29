package com.gurch.sandbox.query;

/** Supported SQL operators for WHERE clauses. */
public enum Operator {
  EQ("="),
  NE("!="),
  GT(">"),
  GTE(">="),
  LT("<"),
  LTE("<="),
  LIKE("LIKE"),
  STARTS_WITH("LIKE"),
  IN("IN"),
  BETWEEN("BETWEEN");

  private final String token;

  Operator(String token) {
    this.token = token;
  }

  /**
   * Returns the SQL token for this operator.
   *
   * @return the SQL string
   */
  public String token() {
    return token;
  }
}
