package com.gurch.sandbox.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.dto.SearchCriteria;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class SearchExecutorTest {

  @Test
  void fallbackLookaheadAvoidsCountQueryWhenNoNextPage() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    SearchExecutor searchExecutor = new SearchExecutor(jdbcTemplate);
    SearchCriteria criteria = criteria(1, 2);
    SQLQueryBuilder builder = SQLQueryBuilder.select("DISTINCT r.status").from("requests", "r");

    when(jdbcTemplate.query(anyString(), anyMap(), ArgumentMatchers.<RowMapper<String>>any()))
        .thenReturn(List.of("REJECTED"));

    PagedResponse<String> response =
        searchExecutor.execute(builder, criteria, (rs, rowNum) -> rs.getString("status"));

    assertThat(response.items()).containsExactly("REJECTED");
    assertThat(response.totalElements()).isEqualTo(3L);
    verify(jdbcTemplate, never()).queryForObject(anyString(), anyMap(), eq(Long.class));
  }

  @Test
  void fallbackLookaheadRunsCountQueryWhenNextPageExists() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    SearchExecutor searchExecutor = new SearchExecutor(jdbcTemplate);
    SearchCriteria criteria = criteria(0, 2);
    SQLQueryBuilder builder = SQLQueryBuilder.select("DISTINCT r.status").from("requests", "r");

    when(jdbcTemplate.query(anyString(), anyMap(), ArgumentMatchers.<RowMapper<String>>any()))
        .thenReturn(List.of("IN_PROGRESS", "COMPLETED", "REJECTED"));
    when(jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Long.class))).thenReturn(3L);

    PagedResponse<String> response =
        searchExecutor.execute(builder, criteria, (rs, rowNum) -> rs.getString("status"));

    assertThat(response.items()).containsExactly("IN_PROGRESS", "COMPLETED");
    assertThat(response.totalElements()).isEqualTo(3L);
    verify(jdbcTemplate).queryForObject(anyString(), anyMap(), eq(Long.class));
  }

  private SearchCriteria criteria(int page, int size) {
    SearchCriteria criteria = new SearchCriteria() {};
    criteria.setPage(page);
    criteria.setSize(size);
    return criteria;
  }
}
