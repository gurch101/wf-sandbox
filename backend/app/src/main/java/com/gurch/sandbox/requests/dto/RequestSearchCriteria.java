package com.gurch.sandbox.requests.dto;

import com.gurch.sandbox.dto.SearchCriteria;
import com.gurch.sandbox.dto.SearchCriteriaUtils;
import com.gurch.sandbox.requests.RequestStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/**
 * Criteria object for searching request records. Supports filtering by request type key, status,
 * and ID, as well as pagination.
 */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching request records")
public class RequestSearchCriteria extends SearchCriteria {
  @Schema(
      description = "Partial request type key to search for (case-insensitive)",
      example = "loan")
  private String nameContains;

  @Schema(description = "Request type keys filter", example = "[\"loan\", \"mortgage\"]")
  private List<String> requestTypeKeys;

  @Schema(
      description = "List of statuses to filter by",
      example = "[\"SUBMITTED\", \"IN_PROGRESS\"]")
  private List<RequestStatus> statuses;

  @Schema(description = "List of specific IDs to filter by", example = "[1, 2, 3]")
  private List<Long> ids;

  @Schema(description = "Task assignee filter", example = "demo")
  private String taskAssignee;

  @Schema(description = "Task assignees filter", example = "[\"demo\", \"john\"]")
  private List<String> taskAssignees;

  /**
   * Returns an uppercase wildcard pattern for request type key filtering.
   *
   * @return uppercase pattern for SQL LIKE, or null when name filter is absent
   */
  public String getNamePattern() {
    return SearchCriteriaUtils.toUpperLikePattern(nameContains);
  }

  /**
   * Returns normalized assignee filters in uppercase for case-insensitive SQL matching.
   *
   * @return uppercase assignee list, or null when no assignee filters are provided
   */
  public List<String> getNormalizedTaskAssignees() {
    List<String> normalized =
        SearchCriteriaUtils.normalizeUppercaseStringList(
            Optional.ofNullable(taskAssignees).orElseGet(List::of));
    if (normalized == null) {
      normalized = new ArrayList<>();
    }

    Optional.ofNullable(taskAssignee)
        .filter(s -> !s.isBlank())
        .map(s -> s.trim().toUpperCase(Locale.ROOT))
        .ifPresent(normalized::add);

    return normalized.isEmpty() ? null : normalized;
  }

  /**
   * Returns normalized request type key filters for SQL IN matching.
   *
   * @return key list, or null when no key filters are provided
   */
  public List<String> getNormalizedRequestTypeKeys() {
    return SearchCriteriaUtils.normalizeStringList(requestTypeKeys);
  }
}
