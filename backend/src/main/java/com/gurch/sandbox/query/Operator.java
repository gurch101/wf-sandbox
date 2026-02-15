package com.gurch.sandbox.query;

public enum Operator {
  EQ("="),
  NE("!="),
  GT(">"),
  GTE(">="),
  LT("<"),
  LTE("<="),
  LIKE("LIKE"),
  IN("IN"),
  BETWEEN("BETWEEN");

  private final String token;

  Operator(String token) {
    this.token = token;
  }

  public String token() {
    return token;
  }
}
