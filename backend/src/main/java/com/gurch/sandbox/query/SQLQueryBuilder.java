package com.gurch.sandbox.query;

import com.gurch.sandbox.query.internal.ParameterRewriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * A fluent builder for constructing SQL queries with named parameters. Supports CTEs, joins, where
 * clauses, grouping, ordering, and pagination.
 */
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

  /**
   * Starts a new query with the specified SELECT clause.
   *
   * @param selectClause the raw SELECT fragment (e.g., "*", "id, name")
   * @return a new builder instance
   */
  public static SQLQueryBuilder select(String selectClause) {
    return new SQLQueryBuilder(selectClause);
  }

  /**
   * Starts a new query with the specified columns.
   *
   * @param columns the columns to select
   * @return a new builder instance
   */
  public static SQLQueryBuilder select(String... columns) {
    StringJoiner joiner = new StringJoiner(", ");
    Arrays.stream(columns).forEach(joiner::add);
    return new SQLQueryBuilder(joiner.toString());
  }

  /**
   * Adds a FROM clause to the query.
   *
   * @param table the table name
   * @param alias the table alias (required)
   * @return this builder
   * @throws IllegalArgumentException if alias is blank
   */
  public SQLQueryBuilder from(String table, String alias) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("FROM alias must not be blank");
    }
    this.fromTable = table;
    this.fromAlias = alias;
    return this;
  }

  /**
   * Adds a JOIN clause to the query.
   *
   * @param type the type of join
   * @param table the table to join
   * @param alias the alias for the joined table
   * @param onClause the raw ON condition
   * @return this builder
   * @throws IllegalArgumentException if alias is blank
   */
  public SQLQueryBuilder join(JoinType type, String table, String alias, String onClause) {
    if (alias == null || alias.isBlank()) {
      throw new IllegalArgumentException("JOIN alias must not be blank");
    }
    joinClauses.add(type.sql() + " " + table + " " + alias + " ON " + onClause);
    return this;
  }

  /**
   * Adds a Common Table Expression (CTE) to the query.
   *
   * @param cteName the name of the CTE
   * @param cteBuilder the builder for the CTE query
   * @return this builder
   * @throws IllegalArgumentException if name is blank or duplicate
   */
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

  /**
   * Adds a WHERE clause with a single condition. If value is null, the condition is skipped.
   *
   * @param column the column name
   * @param operator the operator
   * @param value the value to compare against
   * @return this builder
   */
  public SQLQueryBuilder where(String column, Operator operator, Object value) {
    if (column == null || column.isBlank()) {
      throw new IllegalArgumentException("where column must not be blank");
    }
    Object normalizedValue = normalizeParameterValue(value);
    if (normalizedValue == null) {
      return this;
    }
    if (operator == Operator.IN
        && normalizedValue instanceof Collection<?> collection
        && collection.isEmpty()) {
      return this;
    }
    whereClauses.add(toParameterizedPredicate(column, operator, normalizedValue));
    return this;
  }

  /**
   * Adds a grouped OR condition. Each non-null clause is OR-ed together inside parentheses.
   *
   * @param clauses the clauses to combine
   * @return this builder
   */
  public SQLQueryBuilder whereOr(WhereClause... clauses) {
    List<String> orPredicates = new ArrayList<>();
    for (WhereClause clause : clauses) {
      Object normalizedValue = normalizeParameterValue(clause.value());
      if (normalizedValue == null) {
        continue;
      }
      orPredicates.add(
          toParameterizedPredicate(clause.column(), clause.operator(), normalizedValue));
    }
    if (orPredicates.isEmpty()) {
      return this;
    }
    whereClauses.add("(" + String.join(" OR ", orPredicates) + ")");
    return this;
  }

  /**
   * Adds an IN subquery condition.
   *
   * @param column the column name
   * @param subqueryBuilder the builder for the subquery
   * @return this builder
   */
  public SQLQueryBuilder whereInSubquery(String column, SQLQueryBuilder subqueryBuilder) {
    BuiltQuery subquery = subqueryBuilder.build();
    String rewrittenSubquerySql =
        ParameterRewriter.rewrite(subquery.sql(), subquery.params(), params);
    whereClauses.add(column + " IN (" + rewrittenSubquerySql + ")");
    return this;
  }

  /**
   * Adds an IS NULL condition.
   *
   * @param column the column name
   * @return this builder
   */
  public SQLQueryBuilder whereNull(String column) {
    whereClauses.add(column + " IS NULL");
    return this;
  }

  /**
   * Adds an IS NOT NULL condition.
   *
   * @param column the column name
   * @return this builder
   */
  public SQLQueryBuilder whereNotNull(String column) {
    whereClauses.add(column + " IS NOT NULL");
    return this;
  }

  /**
   * Configures pagination for the query.
   *
   * @param page the zero-indexed page number
   * @param size the number of records per page
   * @return this builder
   * @throws IllegalArgumentException if page is negative or size is non-positive when provided
   */
  public SQLQueryBuilder page(Integer page, Integer size) {
    if (size == null) {
      return this; // Cannot paginate without a size
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be > 0");
    }
    if (page == null) {
      page = 0; // Default page to 0 if null, size is already validated to be > 0
    }
    if (page < 0) {
      throw new IllegalArgumentException("page must be >= 0");
    }
    this.limit = size;
    this.offset = page * size;
    return this;
  }

  /**
   * Adds GROUP BY expressions.
   *
   * @param expressions the expressions to group by
   * @return this builder
   */
  public SQLQueryBuilder groupBy(String... expressions) {
    groupByClauses.addAll(Arrays.asList(expressions));
    return this;
  }

  /**
   * Adds an ORDER BY clause. Supports "-" prefix for DESC and "+" prefix for ASC.
   *
   * @param sortExpressionToken the sort token
   * @return this builder
   */
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

  /**
   * Adds a LIMIT clause.
   *
   * @param limitValue the maximum number of records to return
   * @return this builder
   */
  public SQLQueryBuilder limit(int limitValue) {
    if (limitValue < 0) {
      throw new IllegalArgumentException("limit must be >= 0");
    }
    this.limit = limitValue;
    return this;
  }

  /**
   * Adds an OFFSET clause.
   *
   * @param offsetValue the number of records to skip
   * @return this builder
   */
  public SQLQueryBuilder offset(int offsetValue) {
    if (offsetValue < 0) {
      throw new IllegalArgumentException("offset must be >= 0");
    }
    this.offset = offsetValue;
    return this;
  }

  /**
   * Adds an ORDER BY clause using a whitelist for safety.
   *
   * @param sortToken the sort token from the client
   * @param whitelist the whitelist of allowed sort fields
   * @return this builder
   */
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

  /**
   * Adds a raw WHERE fragment with parameters. Parameters are merged collision-safely.
   *
   * @param fragment the raw SQL fragment
   * @param rawParams the parameters for the fragment
   * @return this builder
   */
  public SQLQueryBuilder rawWhere(String fragment, Map<String, Object> rawParams) {
    if (fragment.contains(";") || fragment.contains("--") || fragment.contains("/*")) {
      throw new IllegalArgumentException("Invalid raw where fragment");
    }
    whereClauses.add(ParameterRewriter.rewrite(fragment, rawParams, params));
    return this;
  }

  /**
   * Builds the final query.
   *
   * @return the built query containing SQL and parameters
   * @throws IllegalStateException if FROM clause is missing
   */
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

  /**
   * Builds a count query for the current state. Useful for pagination total counts. Strips ORDER
   * BY, LIMIT, and OFFSET.
   *
   * @return the built count query
   * @throws IllegalStateException if FROM clause is missing
   */
  public BuiltQuery buildCount() {
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

    String countSelect = "COUNT(*)";
    // If original query has DISTINCT or GROUP BY, we wrap it in a subquery to get accurate count
    if (selectClause.trim().toUpperCase(Locale.ROOT).startsWith("DISTINCT")
        || !groupByClauses.isEmpty()) {
      String innerSql = "SELECT " + selectClause + " FROM " + fromTable + " " + fromAlias;

      if (!joinClauses.isEmpty()) {
        innerSql = innerSql + " " + String.join(" ", joinClauses);
      }
      if (!whereClauses.isEmpty()) {
        innerSql = innerSql + " WHERE " + String.join(" AND ", whereClauses);
      }
      if (!groupByClauses.isEmpty()) {
        innerSql = innerSql + " GROUP BY " + String.join(", ", groupByClauses);
      }
      sqlBuilder.append("SELECT COUNT(*) FROM (").append(innerSql).append(") AS count_target");
    } else {
      String sql = "SELECT " + countSelect + " FROM " + fromTable + " " + fromAlias;
      if (!joinClauses.isEmpty()) {
        sql = sql + " " + String.join(" ", joinClauses);
      }
      if (!whereClauses.isEmpty()) {
        sql = sql + " WHERE " + String.join(" AND ", whereClauses);
      }
      sqlBuilder.append(sql);
    }

    return new BuiltQuery(sqlBuilder.toString(), finalParams);
  }

  private String toParameterizedPredicate(String column, Operator operator, Object value) {
    String paramName = nextParamName();
    params.put(paramName, value);
    if (operator == Operator.IN) {
      return column + " " + operator.token() + " (:" + paramName + ")";
    }
    return column + " " + operator.token() + " :" + paramName;
  }

  private static Object normalizeParameterValue(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof Enum<?> enumValue) {
      return enumValue.name();
    }
    if (value instanceof Collection<?> collection) {
      return collection.stream().map(SQLQueryBuilder::normalizeParameterValue).toList();
    }
    return value;
  }

  private String nextParamName() {
    return "p" + (params.size() + 1);
  }
}
