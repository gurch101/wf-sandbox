package com.gurch.sandbox.query;

public record WhereClause(String column, Operator operator, Object value) {
  public static WhereClause create(String column, Operator operator, Object value) {
    return new WhereClause(column, operator, value);
  }
}
