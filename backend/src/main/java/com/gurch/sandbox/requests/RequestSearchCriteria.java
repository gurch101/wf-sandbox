package com.gurch.sandbox.requests;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

/**
 * Criteria object for searching request records. Supports filtering by name, status, and ID, as
 * well as pagination.
 */
@Value
@Builder
@Jacksonized
@Schema(description = "Criteria for searching request records")
public class RequestSearchCriteria {
  @Schema(description = "Partial name to search for (case-insensitive)", example = "feature")
  String nameContains;

  @Schema(description = "List of statuses to filter by", example = "[\"DRAFT\", \"IN_PROGRESS\"]")
  List<RequestStatus> statuses;

  @Schema(description = "List of specific IDs to filter by", example = "[1, 2, 3]")
  List<Long> ids;

  @Schema(description = "Task assignee filter", example = "demo")
  String taskAssignee;

  @Schema(description = "Task assignees filter", example = "[\"demo\", \"john\"]")
  List<String> taskAssignees;

  @Schema(description = "Zero-indexed page number", example = "0")
  Integer page;

  @Schema(description = "Number of records per page", example = "20")
  Integer size;

  /**
   * Returns an uppercase wildcard pattern for name filtering.
   *
   * @return uppercase pattern for SQL LIKE, or null when name filter is absent
   */
  public String getNamePattern() {
    return Optional.ofNullable(nameContains)
        .filter(s -> !s.isBlank())
        .map(s -> "%" + s.toUpperCase(Locale.ROOT) + "%")
        .orElse(null);
  }

  /**
   * Returns normalized assignee filters in uppercase for case-insensitive SQL matching.
   *
   * @return uppercase assignee list, or null when no assignee filters are provided
   */
  public List<String> getNormalizedTaskAssignees() {
    List<String> normalized = new ArrayList<>();
    Optional.ofNullable(taskAssignee)
        .filter(s -> !s.isBlank())
        .map(s -> s.trim().toUpperCase(Locale.ROOT))
        .ifPresent(normalized::add);

    Optional.ofNullable(taskAssignees).stream()
        .flatMap(List::stream)
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(s -> s.toUpperCase(Locale.ROOT))
        .forEach(normalized::add);

    return normalized.isEmpty() ? null : normalized;
  }
}
