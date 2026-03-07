package com.gurch.sandbox.search;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.dto.SearchCriteria;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.QueryLoggingHelper;
import com.gurch.sandbox.query.SQLQueryBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/** Utility for executing paginated searches using {@link SQLQueryBuilder}. */
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchExecutor {
  private static final String TOTAL_COUNT_ALIAS = "__total_count";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /**
   * Executes a paginated search.
   *
   * @param <T> the result type
   * @param builder the query builder (un-built)
   * @param criteria the search criteria containing pagination info
   * @param rowType the class of the result item
   * @return a paged response
   */
  public <T> PagedResponse<T> execute(
      SQLQueryBuilder builder, SearchCriteria criteria, Class<T> rowType) {
    return execute(builder, criteria, new DataClassRowMapper<>(rowType));
  }

  /**
   * Executes a paginated search with a custom row mapper.
   *
   * @param <T> the result type
   * @param builder the query builder (un-built)
   * @param criteria the search criteria containing pagination info
   * @param rowMapper the row mapper for the result items
   * @return a paged response
   */
  public <T> PagedResponse<T> execute(
      SQLQueryBuilder builder, SearchCriteria criteria, RowMapper<T> rowMapper) {
    int page = criteria.getPage();
    int size = criteria.getSize();

    if (builder.supportsWindowCount()) {
      return executeWithWindowCount(builder, page, size, rowMapper);
    }
    return executeWithLookahead(builder, page, size, rowMapper);
  }

  private <T> PagedResponse<T> executeWithWindowCount(
      SQLQueryBuilder builder, int page, int size, RowMapper<T> rowMapper) {
    builder.page(page, size);
    BuiltQuery dataQuery = builder.buildWithTotalCountWindow(TOTAL_COUNT_ALIAS);
    log.debug(
        QueryLoggingHelper.format("search.data.window", dataQuery, Set.of(TOTAL_COUNT_ALIAS)));

    WindowedRows<T> windowedRows =
        jdbcTemplate.query(
            dataQuery.sql(),
            dataQuery.params(),
            rs -> {
              List<T> items = new ArrayList<>();
              long totalElements = 0L;
              int rowNum = 0;
              while (rs.next()) {
                if (rowNum == 0) {
                  totalElements = rs.getLong(TOTAL_COUNT_ALIAS);
                }
                items.add(rowMapper.mapRow(rs, rowNum++));
              }
              return new WindowedRows<>(items, totalElements);
            });

    if (windowedRows == null || windowedRows.items().isEmpty()) {
      return new PagedResponse<>(List.of(), 0L, page, size);
    }
    return new PagedResponse<>(windowedRows.items(), windowedRows.totalElements(), page, size);
  }

  private <T> PagedResponse<T> executeWithLookahead(
      SQLQueryBuilder builder, int page, int size, RowMapper<T> rowMapper) {
    builder.page(page, size + 1);
    BuiltQuery dataQuery = builder.build();
    log.debug(QueryLoggingHelper.format("search.data.lookahead", dataQuery, Set.of()));

    List<T> rawItems = jdbcTemplate.query(dataQuery.sql(), dataQuery.params(), rowMapper);
    if (rawItems.isEmpty()) {
      return new PagedResponse<>(List.of(), 0L, page, size);
    }

    if (rawItems.size() <= size) {
      long totalElements = ((long) page * size) + rawItems.size();
      return new PagedResponse<>(rawItems, totalElements, page, size);
    }

    List<T> items = List.copyOf(rawItems.subList(0, size));
    BuiltQuery countQuery = builder.buildCount();
    log.debug(QueryLoggingHelper.format("search.count", countQuery, Set.of()));
    Long total = jdbcTemplate.queryForObject(countQuery.sql(), countQuery.params(), Long.class);
    long totalElements = total == null ? 0L : total;

    return new PagedResponse<>(items, totalElements, page, size);
  }

  private record WindowedRows<T>(List<T> items, long totalElements) {}
}
