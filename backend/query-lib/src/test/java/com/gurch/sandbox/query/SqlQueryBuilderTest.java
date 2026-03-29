package com.gurch.sandbox.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SqlQueryBuilderTest {

  @Test
  void chainedSelectCallsAppendColumns() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .select("r.status")
            .from("requests", "r")
            .build();

    assertThat(query.sql()).isEqualTo("SELECT r.id, r.status FROM requests r");
  }

  @Test
  void whereWithNullValueIsOmittedAndQueryBuildsWithNamedParameters() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .where("r.process_instance_id", Operator.EQ, null)
            .build();

    assertThat(query.sql()).contains("r.status = :p1");
    assertThat(query.sql()).doesNotContain("process_instance_id");
    assertThat(query.params()).containsOnlyKeys("p1");
    assertThat(query.params()).containsEntry("p1", "IN_PROGRESS");
  }

  @Test
  void whereOrGroupIsComposedInsideParenthesesAndCombinedWithAnd() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
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
  }

  @Test
  void whereOrSupportsMixedTypedAndRawClauses() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .whereOr(
                WhereClause.create("r.status", Operator.EQ, "IN_PROGRESS"),
                WhereClause.raw(
                    "upper(r.request_type_key) like :namePattern",
                    Map.of("namePattern", "%REQUEST%")))
            .build();

    assertThat(query.sql()).contains("(r.status = :p1 OR upper(r.request_type_key) like :p2)");
    assertThat(query.params()).containsOnlyKeys("p1", "p2");
    assertThat(query.params()).containsEntry("p2", "%REQUEST%");
  }

  @Test
  void whereOrRawClausesRewriteCollidingParameterNamesSafely() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .whereOr(
                WhereClause.raw("upper(r.request_type_key) like :p1", Map.of("p1", "%REQUEST%")))
            .build();

    assertThat(query.sql()).contains("r.status = :p1");
    assertThat(query.sql()).contains("(upper(r.request_type_key) like :p2)");
    assertThat(query.params()).containsOnlyKeys("p1", "p2");
    assertThat(query.params()).containsEntry("p1", "IN_PROGRESS");
    assertThat(query.params()).containsEntry("p2", "%REQUEST%");
  }

  @Test
  void whereOrSupportsMultipleRawClauses() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .whereOr(
                WhereClause.raw(
                    "upper(r.request_type_key) like :namePattern",
                    Map.of("namePattern", "%REQUEST%")),
                WhereClause.raw("r.tenant_id = :tenantId", Map.of("tenantId", 42)))
            .build();

    assertThat(query.sql()).contains("(upper(r.request_type_key) like :p1 OR r.tenant_id = :p2)");
    assertThat(query.params()).containsOnlyKeys("p1", "p2");
    assertThat(query.params()).containsEntry("p1", "%REQUEST%");
    assertThat(query.params()).containsEntry("p2", 42);
  }

  @Test
  void whereNullEmitsIsNullPredicate() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .whereNull("r.process_instance_id")
            .build();

    assertThat(query.sql()).contains("r.process_instance_id IS NULL");
    assertThat(query.params()).isEmpty();
  }

  @Test
  void startsWithUsesLikeAndAppendsWildcardSuffix() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.request_type_key", Operator.STARTS_WITH, "REQ")
            .build();

    assertThat(query.sql()).contains("r.request_type_key LIKE :p1");
    assertThat(query.params()).containsEntry("p1", "REQ%");
  }

  @Test
  void startsWithWithNullValueIsOmitted() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .where("r.request_type_key", Operator.STARTS_WITH, null)
            .build();

    assertThat(query.sql()).contains("r.version > :p1");
    assertThat(query.sql()).doesNotContain("r.request_type_key LIKE");
    assertThat(query.params()).containsOnlyKeys("p1");
  }

  @Test
  void startsWithRejectsNonStringValues() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .where("r.request_type_key", Operator.STARTS_WITH, 42)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("STARTS_WITH");
  }

  @Test
  void whereOrWithOnlyNullValuedClausesIsOmitted() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .whereOr(
                WhereClause.create("r.status", Operator.EQ, null),
                WhereClause.create("r.request_type_key", Operator.LIKE, null))
            .build();

    assertThat(query.sql()).contains("r.version > :p1");
    assertThat(query.sql()).doesNotContain(" OR ");
    assertThat(query.sql()).doesNotContain("()");
    assertThat(query.params()).containsOnlyKeys("p1");
  }

  @Test
  void blankAliasIsRejected() {
    assertThatThrownBy(
            () -> SQLQueryBuilder.newBuilder().select("r.id").from("requests", "").build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alias");
  }

  @Test
  void buildWithoutFromIsRejected() {
    assertThatThrownBy(() -> SQLQueryBuilder.newBuilder().select("r.id").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("FROM");
  }

  @Test
  void buildWithoutSelectIsRejected() {
    assertThatThrownBy(() -> SQLQueryBuilder.newBuilder().from("requests", "r").build())
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("SELECT");
  }

  @Test
  void pageAddsLimitAndOffset() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .page(1, 2)
            .build();

    assertThat(query.sql()).contains("OFFSET 2 ROWS").contains("FETCH NEXT 2 ROWS ONLY");
  }

  @Test
  void pageWithNullPageOrSizeIsOmitted() {
    BuiltQuery nullPageQuery =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .page(null, 2)
            .build();

    BuiltQuery nullSizeQuery =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .page(0, null)
            .build();

    assertThat(nullPageQuery.sql()).contains("OFFSET 0 ROWS").contains("FETCH NEXT 2 ROWS ONLY");
    assertThat(nullSizeQuery.sql()).doesNotContain("FETCH").doesNotContain("OFFSET");
  }

  @Test
  void safeOrderByUsesWhitelistedExpressionAndDirection() {
    SortWhitelist whitelist =
        SortWhitelist.create().allow("createdAt", "r.created_at").allow("status", "r.status");

    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .safeOrderBy("-createdAt", whitelist)
            .build();

    assertThat(query.sql()).contains("ORDER BY r.created_at DESC");
  }

  @Test
  void safeOrderByRejectsUnknownField() {
    SortWhitelist whitelist = SortWhitelist.create().allow("createdAt", "r.created_at");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
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
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .safeOrderBy("status", whitelist)
            .build();
    BuiltQuery plusPrefixed =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .safeOrderBy("+status", whitelist)
            .build();

    assertThat(unprefixed.sql()).contains("ORDER BY r.status ASC");
    assertThat(plusPrefixed.sql()).contains("ORDER BY r.status ASC");
  }

  @Test
  void rawWhereIncludesFragmentAndMergesParamsCollisionSafely() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .rawWhere(
                "upper(r.request_type_key) like :namePattern and r.status = :p1",
                Map.of("namePattern", "%REQUEST%", "p1", "IN_PROGRESS"))
            .build();

    assertThat(query.sql()).contains("upper(r.request_type_key) like :p2 and r.status = :p3");
    assertThat(query.params()).containsOnlyKeys("p1", "p2", "p3");
  }

  @Test
  void rawWhereRejectsStatementDelimitersAndComments() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .rawWhere(
                        "r.status = :status; DELETE FROM requests", Map.of("status", "IN_PROGRESS"))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("raw");
  }

  @Test
  void whereOrRawRejectsStatementDelimitersAndComments() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .whereOr(
                        WhereClause.raw(
                            "r.status = :status; DELETE FROM requests",
                            Map.of("status", "IN_PROGRESS")))
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("raw");
  }

  @Test
  void joinAddsJoinClauseAndSupportsFilteringByJoinedTable() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .join(JoinType.INNER, "requests", "r2", "r2.id = r.id")
            .where("r2.status", Operator.EQ, "IN_PROGRESS")
            .build();

    assertThat(query.sql()).contains("INNER JOIN requests r2 ON r2.id = r.id");
    assertThat(query.sql()).contains("r2.status = :p1");
  }

  @Test
  void joinRejectsBlankAlias() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .join(JoinType.LEFT, "requests", " ", "r.id = r.id")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("alias");
  }

  @Test
  void groupByOrderByLimitAndOffsetAreComposedInSql() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.status", "count(*) as cnt")
            .from("requests", "r")
            .groupBy("r.status")
            .orderBy("r.status")
            .limit(2)
            .offset(0)
            .build();

    assertThat(query.sql()).contains("GROUP BY r.status");
    assertThat(query.sql()).contains("ORDER BY r.status ASC");
    assertThat(query.sql()).contains("OFFSET 0 ROWS");
    assertThat(query.sql()).contains("FETCH NEXT 2 ROWS ONLY");
  }

  @Test
  void postgresDialectUsesLimitAndOffsetSyntax() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .dialect(SqlDialect.POSTGRES)
            .limit(2)
            .offset(1)
            .build();

    assertThat(query.sql()).contains("LIMIT 2").contains("OFFSET 1");
  }

  @Test
  void orderBySupportsSignedTokens() {
    BuiltQuery descending =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .orderBy("-r.created_at")
            .build();
    BuiltQuery explicitAscending =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .orderBy("+r.created_at")
            .build();
    BuiltQuery defaultAscending =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .orderBy("r.created_at")
            .build();

    assertThat(descending.sql()).contains("ORDER BY r.created_at DESC");
    assertThat(explicitAscending.sql()).contains("ORDER BY r.created_at ASC");
    assertThat(defaultAscending.sql()).contains("ORDER BY r.created_at ASC");
  }

  @Test
  void negativeLimitAndOffsetAreRejected() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder().select("r.id").from("requests", "r").limit(-1).build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .offset(-1)
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("offset");
  }

  @Test
  void withSupportsMultipleCtesInDeclaredOrder() {
    SQLQueryBuilder baseCte =
        SQLQueryBuilder.newBuilder()
            .select("r.id", "r.status")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L);

    SQLQueryBuilder inProgressCte =
        SQLQueryBuilder.newBuilder()
            .select("b.id")
            .from("base", "b")
            .where("b.status", Operator.EQ, "IN_PROGRESS");

    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("ip.id")
            .with("base", baseCte)
            .with("in_progress", inProgressCte)
            .from("in_progress", "ip")
            .build();

    assertThat(query.sql()).startsWith("WITH base AS (");
    assertThat(query.sql()).contains("), in_progress AS (");
  }

  @Test
  void withRejectsDuplicateCteNames() {
    SQLQueryBuilder cte = SQLQueryBuilder.newBuilder().select("r.id").from("requests", "r");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("x.id")
                    .with("dup", cte)
                    .with("dup", cte)
                    .from("dup", "x")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("CTE");
  }

  @Test
  void whereInSubqueryEmbedsSubquery() {
    SQLQueryBuilder subquery =
        SQLQueryBuilder.newBuilder()
            .select("r2.id")
            .from("requests", "r2")
            .where("r2.status", Operator.EQ, "COMPLETED");

    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .whereInSubquery("r.id", subquery)
            .build();

    assertThat(query.sql())
        .contains("r.id IN (SELECT r2.id FROM requests r2 WHERE r2.status = :p1)");
    assertThat(query.params()).containsOnlyKeys("p1");
  }

  @Test
  void whereInSubqueryPropagatesInvalidSubqueryState() {
    SQLQueryBuilder invalidSubquery = SQLQueryBuilder.newBuilder().select("x.id");

    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
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
                SQLQueryBuilder.newBuilder()
                    .select("r.id")
                    .from("requests", "r")
                    .where(" ", Operator.EQ, "IN_PROGRESS")
                    .build())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("column");
  }

  @Test
  void whereInWithEmptyCollectionIsOmitted() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.version", Operator.GT, 0L)
            .where("r.status", Operator.IN, Collections.emptyList())
            .build();

    assertThat(query.sql()).contains("r.version > :p1");
    assertThat(query.sql()).doesNotContain("r.status IN");
    assertThat(query.params()).containsOnlyKeys("p1");
  }

  @Test
  void whereInWithCollectionUsesSingleNamedParameter() {
    List<String> statuses = List.of("IN_PROGRESS", "COMPLETED");
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.IN, statuses)
            .build();

    assertThat(query.sql()).contains("r.status IN (:p1)");
    assertThat(query.params()).containsEntry("p1", statuses);
  }

  @Test
  void whereMapsEnumToPersistedStringValue() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.EQ, LocalStatus.IN_PROGRESS)
            .build();

    assertThat(query.params()).containsEntry("p1", "IN_PROGRESS");
  }

  @Test
  void whereInMapsEnumCollectionToPersistedStringValues() {
    List<LocalStatus> statuses = List.of(LocalStatus.IN_PROGRESS, LocalStatus.COMPLETED);
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .where("r.status", Operator.IN, statuses)
            .build();

    assertThat(query.params()).containsEntry("p1", List.of("IN_PROGRESS", "COMPLETED"));
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

  @Test
  void buildCountGeneratesSimpleCountSql() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id", "r.status")
            .from("requests", "r")
            .where("r.status", Operator.EQ, "IN_PROGRESS")
            .buildCount();

    assertThat(query.sql()).isEqualTo("SELECT COUNT(*) FROM requests r WHERE r.status = :p1");
    assertThat(query.params()).containsEntry("p1", "IN_PROGRESS");
  }

  @Test
  void buildCountWrapsDistinctQueriesInSubquery() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("DISTINCT r.status")
            .from("requests", "r")
            .orderBy("r.status")
            .page(0, 10)
            .buildCount();

    assertThat(query.sql())
        .isEqualTo("SELECT COUNT(*) FROM (SELECT DISTINCT r.status FROM requests r) count_target");
  }

  @Test
  void buildCountWrapsGroupByQueriesInSubquery() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.status", "count(*)")
            .from("requests", "r")
            .groupBy("r.status")
            .buildCount();

    assertThat(query.sql())
        .isEqualTo(
            "SELECT COUNT(*) FROM (SELECT r.status, count(*) FROM requests r GROUP BY r.status) count_target");
  }

  @Test
  void buildWithTotalCountWindowAddsWindowCountColumn() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .orderBy("r.id")
            .page(0, 2)
            .buildWithTotalCountWindow("__total_count");

    assertThat(query.sql()).contains("COUNT(*) OVER() AS __total_count");
    assertThat(query.sql()).contains("FETCH NEXT 2 ROWS ONLY");
  }

  @Test
  void buildWithTotalCountWindowRejectsDistinctSelect() {
    assertThatThrownBy(
            () ->
                SQLQueryBuilder.newBuilder()
                    .select("DISTINCT r.status")
                    .from("requests", "r")
                    .buildWithTotalCountWindow("__total_count"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Window total count");
  }

  @Test
  void buildCountStripsOrderByAndPagination() {
    BuiltQuery query =
        SQLQueryBuilder.newBuilder()
            .select("r.id")
            .from("requests", "r")
            .orderBy("r.id")
            .page(1, 1)
            .buildCount();

    assertThat(query.sql()).isEqualTo("SELECT COUNT(*) FROM requests r");
  }

  private BuiltQuery createDeterministicFixtureQuery() {
    Map<String, Object> rawParams = new LinkedHashMap<>();
    rawParams.put("namePattern", "%REQUEST%");
    rawParams.put("p1", "IN_PROGRESS");

    return SQLQueryBuilder.newBuilder()
        .select("r.id")
        .from("requests", "r")
        .where("r.version", Operator.GT, 0L)
        .whereOr(
            WhereClause.create("r.status", Operator.EQ, "IN_PROGRESS"),
            WhereClause.create("r.status", Operator.EQ, "COMPLETED"))
        .rawWhere("upper(r.request_type_key) like :namePattern and r.status = :p1", rawParams)
        .safeOrderBy("-createdAt", SortWhitelist.create().allow("createdAt", "r.created_at"))
        .page(0, 10)
        .build();
  }

  private enum LocalStatus {
    IN_PROGRESS,
    COMPLETED
  }
}
