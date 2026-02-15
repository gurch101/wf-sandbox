package com.gurch.sandbox.query;

import com.gurch.sandbox.query.internal.ParameterRewriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class SQLQueryBuilder {
  private final String selectClause;
  private String fromTable;
  private String fromAlias;
  private final List<String> joinClauses = new ArrayList<>();
  private final List<String> groupByClauses = new ArrayList<>();
  private final Map<String, SQLQueryBuilder> ctes = new LinkedHashMap<>();
  private final Map<String, Object> params = new LinkedHashMap<>();
  private final List<String> whereClauses = new ArrayList<>();
  private final List<String> orderByClauses = new ArrayList<>();
  private Integer limit;
  private Integer offset;

  private SQLQueryBuilder(String selectClause) {
    this.selectClause = selectClause;
  }

  public static SQLQueryBuilder select(String selectClause) {
    return new SQLQueryBuilder(selectClause);
  }

  public static SQLQueryBuilder select(String... columns) {
    StringJoiner joiner = new StringJoiner(", ");
    Arrays.stream(columns).forEach(joiner::add);
    return new SQLQueryBuilder(joiner.toString());
  }

  public SQLQueryBuilder from(String table, String alias) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("FROM alias must not be blank");
    }
    this.fromTable = table;
    this.fromAlias = alias;
    return this;
  }

  public SQLQueryBuilder join(JoinType type, String table, String alias, String onClause) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("JOIN alias must not be blank");
    }
    joinClauses.add(type.sql() + " " + table + " " + alias + " ON " + onClause);
    return this;
  }

  public SQLQueryBuilder with(String cteName, SQLQueryBuilder cteBuilder) {
    if (cteName == null || cteName.isBlank()) {
      throw new IllegalArgumentException("CTE name must not be blank");
    }
    if (ctes.containsKey(cteName)) {
      throw new IllegalArgumentException("Duplicate CTE name: " + cteName);
    }
    ctes.put(cteName, cteBuilder);
    return this;
  }

  public SQLQueryBuilder where(String column, Operator operator, Object value) {
    if (column == null || column.isBlank()) {
      throw new IllegalArgumentException("where column must not be blank");
    }
    if (value == null) {
      return this;
    }
    if (operator == Operator.IN
        && value instanceof Collection<?> collection
        && collection.isEmpty()) {
      return this;
    }
    whereClauses.add(toParameterizedPredicate(column, operator, value));
    return this;
  }

  public SQLQueryBuilder whereOr(WhereClause... clauses) {
    List<String> orPredicates = new ArrayList<>();
    for (WhereClause clause : clauses) {
      if (clause.value() == null) {
        continue;
      }
      orPredicates.add(
          toParameterizedPredicate(clause.column(), clause.operator(), clause.value()));
    }
    if (orPredicates.isEmpty()) {
      return this;
    }
    whereClauses.add("(" + String.join(" OR ", orPredicates) + ")");
    return this;
  }

  public SQLQueryBuilder whereInSubquery(String column, SQLQueryBuilder subqueryBuilder) {
    BuiltQuery subquery = subqueryBuilder.build();
    String rewrittenSubquerySql =
        ParameterRewriter.rewrite(subquery.sql(), subquery.params(), params);
    whereClauses.add(column + " IN (" + rewrittenSubquerySql + ")");
    return this;
  }

  public SQLQueryBuilder whereNull(String column) {
    whereClauses.add(column + " IS NULL");
    return this;
  }

  public SQLQueryBuilder whereNotNull(String column) {
    whereClauses.add(column + " IS NOT NULL");
    return this;
  }

  public SQLQueryBuilder page(int page, int size) {
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be > 0");
    }
    this.limit = size;
    this.offset = page * size;
    return this;
  }

  public SQLQueryBuilder groupBy(String... expressions) {
    groupByClauses.addAll(Arrays.asList(expressions));
    return this;
  }

  public SQLQueryBuilder orderBy(String sortExpressionToken) {
    if (sortExpressionToken == null || sortExpressionToken.isBlank()) {
      throw new IllegalArgumentException("Sort token must not be blank");
    }
    String normalizedToken = sortExpressionToken.trim();
    String direction = "ASC";
    if (normalizedToken.startsWith("-")) {
      direction = "DESC";
      normalizedToken = normalizedToken.substring(1);
    } else if (normalizedToken.startsWith("+")) {
      normalizedToken = normalizedToken.substring(1);
    }
    if (normalizedToken.isBlank()) {
      throw new IllegalArgumentException("Sort token must include an expression");
    }
    orderByClauses.add(normalizedToken + " " + direction);
    return this;
  }

  public SQLQueryBuilder limit(int limitValue) {
    if (limitValue < 0) {
      throw new IllegalArgumentException("limit must be >= 0");
    }
    this.limit = limitValue;
    return this;
  }

  public SQLQueryBuilder offset(int offsetValue) {
    if (offsetValue < 0) {
      throw new IllegalArgumentException("offset must be >= 0");
    }
    this.offset = offsetValue;
    return this;
  }

  public SQLQueryBuilder safeOrderBy(String sortToken, SortWhitelist whitelist) {
    if (sortToken == null || sortToken.isBlank()) {
      throw new IllegalArgumentException("Sort token must not be blank");
    }
    String normalizedToken = sortToken.trim();
    String direction = "ASC";
    if (normalizedToken.startsWith("-")) {
      direction = "DESC";
      normalizedToken = normalizedToken.substring(1);
    } else if (normalizedToken.startsWith("+")) {
      normalizedToken = normalizedToken.substring(1);
    }
    if (normalizedToken.isBlank()) {
      throw new IllegalArgumentException("Sort token must include a field");
    }
    String expression = whitelist.resolve(normalizedToken);
    if (expression == null) {
      throw new IllegalArgumentException("Unknown sort field: " + normalizedToken);
    }
    orderByClauses.add(expression + " " + direction);
    return this;
  }

  public SQLQueryBuilder rawWhere(String fragment, Map<String, Object> rawParams) {
    if (fragment.contains(";") || fragment.contains("--") || fragment.contains("/*")) {
      throw new IllegalArgumentException("Invalid raw where fragment");
    }
    whereClauses.add(ParameterRewriter.rewrite(fragment, rawParams, params));
    return this;
  }

  public BuiltQuery build() {
    if (fromTable == null || fromAlias == null) {
      throw new IllegalStateException("FROM clause is required");
    }
    Map<String, Object> finalParams = new LinkedHashMap<>(params);
    StringBuilder sqlBuilder = new StringBuilder();
    if (!ctes.isEmpty()) {
      List<String> cteParts = new ArrayList<>();
      for (Map.Entry<String, SQLQueryBuilder> cte : ctes.entrySet()) {
        BuiltQuery cteQuery = cte.getValue().build();
        String rewrittenCteSql =
            ParameterRewriter.rewrite(cteQuery.sql(), cteQuery.params(), finalParams);
        cteParts.add(cte.getKey() + " AS (" + rewrittenCteSql + ")");
      }
      sqlBuilder.append("WITH ").append(String.join(", ", cteParts)).append(" ");
    }

    String sql = "SELECT " + selectClause + " FROM " + fromTable + " " + fromAlias;
    if (!joinClauses.isEmpty()) {
      sql = sql + " " + String.join(" ", joinClauses);
    }
    if (!whereClauses.isEmpty()) {
      sql = sql + " WHERE " + String.join(" AND ", whereClauses);
    }
    if (!groupByClauses.isEmpty()) {
      sql = sql + " GROUP BY " + String.join(", ", groupByClauses);
    }
    if (!orderByClauses.isEmpty()) {
      sql = sql + " ORDER BY " + String.join(", ", orderByClauses);
    }
    if (limit != null) {
      sql = sql + " LIMIT " + limit;
    }
    if (offset != null) {
      sql = sql + " OFFSET " + offset;
    }
    sqlBuilder.append(sql);
    return new BuiltQuery(sqlBuilder.toString(), finalParams);
  }

  private String toParameterizedPredicate(String column, Operator operator, Object value) {
    String paramName = nextParamName();
    params.put(paramName, value);
    return column + " " + operator.token() + " :" + paramName;
  }

  private String nextParamName() {
    return "p" + (params.size() + 1);
  }
}
