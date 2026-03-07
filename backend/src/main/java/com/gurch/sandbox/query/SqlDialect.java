package com.gurch.sandbox.query;

/** SQL dialect hints used by {@link SQLQueryBuilder}. */
public enum SqlDialect {
  /** Uses SQL-standard pagination syntax supported by Oracle and modern PostgreSQL. */
  ANSI {
    @Override
    void appendPagination(StringBuilder sql, Integer limit, Integer offset) {
      if (offset != null) {
        sql.append(" OFFSET ").append(offset).append(" ROWS");
      }
      if (limit != null) {
        if (offset != null) {
          sql.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
        } else {
          sql.append(" FETCH FIRST ").append(limit).append(" ROWS ONLY");
        }
      }
    }
  },

  /** PostgreSQL-native pagination syntax. */
  POSTGRES {
    @Override
    void appendPagination(StringBuilder sql, Integer limit, Integer offset) {
      if (limit != null) {
        sql.append(" LIMIT ").append(limit);
      }
      if (offset != null) {
        sql.append(" OFFSET ").append(offset);
      }
    }
  };

  abstract void appendPagination(StringBuilder sql, Integer limit, Integer offset);
}
