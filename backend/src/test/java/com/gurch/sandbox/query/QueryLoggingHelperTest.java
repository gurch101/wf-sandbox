package com.gurch.sandbox.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QueryLoggingHelperTest {

  @Test
  void formatIncludesSqlParamKeysAndRedactsConfiguredParams() {
    Map<String, Object> params = new LinkedHashMap<>();
    params.put("email", "person@example.com");
    params.put("status", "IN_PROGRESS");

    BuiltQuery query = new BuiltQuery("SELECT r.id FROM requests r WHERE r.status = :p1", params);

    String logLine = QueryLoggingHelper.format("query-123", query, Set.of("email"));

    assertThat(logLine).contains("queryId=query-123");
    assertThat(logLine).contains("sql=SELECT r.id FROM requests r WHERE r.status = :p1");
    assertThat(logLine).contains("paramKeys=[email, status]");
    assertThat(logLine).contains("redactedParams={email=***, status=IN_PROGRESS}");
  }
}
