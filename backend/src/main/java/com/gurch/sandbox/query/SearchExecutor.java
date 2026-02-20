package com.gurch.sandbox.query;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.dto.SearchCriteria;
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

    // 1. Get total count
    BuiltQuery countQuery = builder.buildCount();
    log.debug(QueryLoggingHelper.format("search.count", countQuery, Set.of()));
    Long total = jdbcTemplate.queryForObject(countQuery.sql(), countQuery.params(), Long.class);
    long totalElements = total == null ? 0L : total;

    if (totalElements == 0) {
      return new PagedResponse<>(List.of(), 0, criteria.getPage(), criteria.getSize());
    }

    // 2. Apply pagination to builder and build data query
    builder.page(criteria.getPage(), criteria.getSize());
    BuiltQuery dataQuery = builder.build();
    log.debug(QueryLoggingHelper.format("search.data", dataQuery, Set.of()));

    List<T> items = jdbcTemplate.query(dataQuery.sql(), dataQuery.params(), rowMapper);

    return new PagedResponse<>(items, totalElements, criteria.getPage(), criteria.getSize());
  }
}
