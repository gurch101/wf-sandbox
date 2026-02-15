package com.gurch.sandbox.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gurch.sandbox.AbstractJdbcIntegrationTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SqlQueryBuilderTest extends AbstractJdbcIntegrationTest {

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @BeforeEach
  void setUp() {
    jdbcTemplate.getJdbcTemplate().execute("DELETE FROM requests");

    insertRequest("Request A", "IN_PROGRESS", null, 1L);
    insertRequest("Request B", "COMPLETED", "pi-1", 2L);
    insertRequest("Request C", "REJECTED", "pi-2", 3L);
  }

  @Test
  void whereWithNullValueIsOmittedAndQueryExecutesWithNamedParameters() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .where("r.process_instance_id", Operator.EQ, null)
            .build();

    assertThat(query.sql()).contains("r.status = :p1");
    assertThat(query.sql()).doesNotContain("process_instance_id");
    assertThat(query.params()).containsOnlyKeys("p1");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void whereOrGroupIsComposedInsideParenthesesAndCombinedWithAnd() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .whereOr(
                WhereClause.create("r.status", Operator.EQ, "IN_PROGRESS"),
                WhereClause.create("r.status", Operator.EQ, "COMPLETED"))
            .build();

    assertThat(query.sql())
        .contains("r.version > :p1")
        .contains("(r.status = :p2 OR r.status = :p3)");
    assertThat(query.params()).containsOnlyKeys("p1", "p2", "p3");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(2);
  }

  @Test
  void whereNullEmitsIsNullPredicate() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .whereNull("r.process_instance_id")
            .build();

    assertThat(query.sql()).contains("r.process_instance_id IS NULL");
    assertThat(query.params()).isEmpty();

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void whereOrWithOnlyNullValuedClausesIsOmitted() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .whereOr(
                WhereClause.create("r.status", Operator.EQ, null),
                WhereClause.create("r.name", Operator.LIKE, null))
            .build();

    assertThat(query.sql()).contains("r.version > :p1");
    assertThat(query.sql()).doesNotContain(" OR ");
    assertThat(query.sql()).doesNotContain("()");
    assertThat(query.params()).containsOnlyKeys("p1");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(3);
  }

  @Test
  void blankAliasIsRejected() {
    assertThatThrownBy(() -> SQLQueryBuilder.select("r.id").from("requests", "").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alias");
  }

  @Test
  void buildWithoutFromIsRejected() {
    assertThatThrownBy(() -> SQLQueryBuilder.select("r.id").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FROM");
  }

  @Test
  void pageAddsLimitAndOffset() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .page(1, 2)
            .build();

    assertThat(query.sql()).contains("LIMIT 2").contains("OFFSET 2");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void safeOrderByUsesWhitelistedExpressionAndDirection() {
    SortWhitelist whitelist =
        SortWhitelist.create().allow("createdAt", "r.created_at").allow("status", "r.status");

    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .safeOrderBy("-createdAt", whitelist)
            .build();

    assertThat(query.sql()).contains("ORDER BY r.created_at DESC");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).isNotEmpty();
  }

  @Test
  void safeOrderByRejectsUnknownField() {
    SortWhitelist whitelist = SortWhitelist.create().allow("createdAt", "r.created_at");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("r.id")
                    .from("requests", "r")
                    .safeOrderBy("dropTable", whitelist)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sort");
  }

  @Test
  void safeOrderByDefaultsToAscendingAndSupportsExplicitPlusPrefix() {
    SortWhitelist whitelist = SortWhitelist.create().allow("status", "r.status");

    BuiltQuery unprefixed =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .safeOrderBy("status", whitelist)
            .build();
    BuiltQuery plusPrefixed =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .safeOrderBy("+status", whitelist)
            .build();

    assertThat(unprefixed.sql()).contains("ORDER BY r.status ASC");
    assertThat(plusPrefixed.sql()).contains("ORDER BY r.status ASC");
  }

  @Test
  void rawWhereIncludesFragmentAndMergesParamsCollisionSafely() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .rawWhere(
                "upper(r.name) like :namePattern and r.status = :p1",
                Map.of("namePattern", "%REQUEST%", "p1", "IN_PROGRESS"))
            .build();

    assertThat(query.sql()).contains("upper(r.name) like :p2 and r.status = :p3");
    assertThat(query.params()).containsOnlyKeys("p1", "p2", "p3");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void rawWhereRejectsStatementDelimitersAndComments() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("r.id")
                    .from("requests", "r")
                    .rawWhere(
                        "r.status = :status; DELETE FROM requests", Map.of("status", "IN_PROGRESS"))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("raw");
  }

  @Test
  void joinAddsJoinClauseAndSupportsFilteringByJoinedTable() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .join(JoinType.INNER, "requests", "r2", "r2.id = r.id")
            .where("r2.status", Operator.EQ, "IN_PROGRESS")
            .build();

    assertThat(query.sql()).contains("INNER JOIN requests r2 ON r2.id = r.id");
    assertThat(query.sql()).contains("r2.status = :p1");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void joinRejectsBlankAlias() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("r.id")
                    .from("requests", "r")
                    .join(JoinType.LEFT, "requests", " ", "r.id = r.id")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alias");
  }

  @Test
  void groupByOrderByLimitAndOffsetAreComposedInSql() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.status", "count(*) as cnt")
            .from("requests", "r")
            .groupBy("r.status")
            .orderBy("r.status")
            .limit(2)
            .offset(0)
            .build();

    assertThat(query.sql()).contains("GROUP BY r.status");
    assertThat(query.sql()).contains("ORDER BY r.status ASC");
    assertThat(query.sql()).contains("LIMIT 2");
    assertThat(query.sql()).contains("OFFSET 0");
  }

  @Test
  void orderBySupportsSignedTokens() {
    BuiltQuery descending =
        SQLQueryBuilder.select("r.id").from("requests", "r").orderBy("-r.created_at").build();
    BuiltQuery explicitAscending =
        SQLQueryBuilder.select("r.id").from("requests", "r").orderBy("+r.created_at").build();
    BuiltQuery defaultAscending =
        SQLQueryBuilder.select("r.id").from("requests", "r").orderBy("r.created_at").build();

    assertThat(descending.sql()).contains("ORDER BY r.created_at DESC");
    assertThat(explicitAscending.sql()).contains("ORDER BY r.created_at ASC");
    assertThat(defaultAscending.sql()).contains("ORDER BY r.created_at ASC");
  }

  @Test
  void negativeLimitAndOffsetAreRejected() {
    assertThatThrownBy(() -> SQLQueryBuilder.select("r.id").from("requests", "r").limit(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit");

    assertThatThrownBy(
            () -> SQLQueryBuilder.select("r.id").from("requests", "r").offset(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offset");
  }

  @Test
  void withSupportsMultipleCtesInDeclaredOrder() {
    SQLQueryBuilder baseCte =
        SQLQueryBuilder.select("r.id", "r.status")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L);

    SQLQueryBuilder inProgressCte =
        SQLQueryBuilder.select("b.id")
            .from("base", "b")
            .where("b.status", Operator.EQ, "IN_PROGRESS");

    BuiltQuery query =
        SQLQueryBuilder.select("ip.id")
            .with("base", baseCte)
            .with("in_progress", inProgressCte)
            .from("in_progress", "ip")
            .build();

    assertThat(query.sql()).startsWith("WITH base AS (");
    assertThat(query.sql()).contains("), in_progress AS (");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void withRejectsDuplicateCteNames() {
    SQLQueryBuilder cte = SQLQueryBuilder.select("r.id").from("requests", "r");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("x.id")
                    .with("dup", cte)
                    .with("dup", cte)
                    .from("dup", "x")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTE");
  }

  @Test
  void whereInSubqueryEmbedsSubqueryAndExecutes() {
    SQLQueryBuilder subquery =
        SQLQueryBuilder.select("r2.id")
            .from("requests", "r2")
            .where("r2.status", Operator.EQ, "COMPLETED");

    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .whereInSubquery("r.id", subquery)
            .build();

    assertThat(query.sql())
        .contains("r.id IN (SELECT r2.id FROM requests r2 WHERE r2.status = :p1)");
    assertThat(query.params()).containsOnlyKeys("p1");

    List<Long> ids = jdbcTemplate.queryForList(query.sql(), query.params(), Long.class);
    assertThat(ids).hasSize(1);
  }

  @Test
  void whereInSubqueryPropagatesInvalidSubqueryState() {
    SQLQueryBuilder invalidSubquery = SQLQueryBuilder.select("x.id");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("r.id")
                    .from("requests", "r")
                    .whereInSubquery("r.id", invalidSubquery)
                    .build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FROM");
  }

  @Test
  void whereRejectsBlankColumn() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.select("r.id")
                    .from("requests", "r")
                    .where(" ", Operator.EQ, "IN_PROGRESS")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("column");
  }

  @Test
  void whereInWithEmptyCollectionIsOmitted() {
    BuiltQuery query =
        SQLQueryBuilder.select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .where("r.status", Operator.IN, Collections.emptyList())
            .build();

    assertThat(query.sql()).contains("r.version > :p1");
    assertThat(query.sql()).doesNotContain("r.status IN");
    assertThat(query.params()).containsOnlyKeys("p1");
  }

  @Test
  void identicalBuilderCallsProduceDeterministicSqlAndParamKeyOrder() {
    BuiltQuery first = createDeterministicFixtureQuery();
    BuiltQuery second = createDeterministicFixtureQuery();

    assertThat(second.sql()).isEqualTo(first.sql());
    assertThat(new ArrayList<>(second.params().keySet()))
        .isEqualTo(new ArrayList<>(first.params().keySet()));
    assertThat(second.params()).isEqualTo(first.params());
  }

  private BuiltQuery createDeterministicFixtureQuery() {
    Map<String, Object> rawParams = new LinkedHashMap<>();
    rawParams.put("namePattern", "%REQUEST%");
    rawParams.put("p1", "IN_PROGRESS");

    return SQLQueryBuilder.select("r.id")
        .from("requests", "r")
        .where("r.version", Operator.GT, 0L)
        .whereOr(
            WhereClause.create("r.status", Operator.EQ, "IN_PROGRESS"),
            WhereClause.create("r.status", Operator.EQ, "COMPLETED"))
        .rawWhere("upper(r.name) like :namePattern and r.status = :p1", rawParams)
        .safeOrderBy("-createdAt", SortWhitelist.create().allow("createdAt", "r.created_at"))
        .page(0, 10)
        .build();
  }

  private void insertRequest(String name, String status, String processInstanceId, long version) {
    jdbcTemplate.update(
        """
        INSERT INTO requests (name, status, process_instance_id, created_at, updated_at, version)
        VALUES (:name, :status, :processInstanceId, :createdAt, :updatedAt, :version)
        """,
        new MapSqlParameterSource()
            .addValue("name", name)
            .addValue("status", status)
            .addValue("processInstanceId", processInstanceId)
            .addValue("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
            .addValue("updatedAt", OffsetDateTime.now(ZoneOffset.UTC))
            .addValue("version", version));
  }
}
