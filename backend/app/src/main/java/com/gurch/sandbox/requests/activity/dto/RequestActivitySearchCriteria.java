package com.gurch.sandbox.requests.activity.dto;

import com.gurch.sandbox.dto.SearchCriteria;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;

/** Search criteria for filtering and paging request activity timeline entries. */
@Getter
@Setter
@SuperBuilder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Schema(description = "Criteria for searching request activity events")
public class RequestActivitySearchCriteria extends SearchCriteria {

  @Schema(
      description = "Optional activity event type filters",
      example = "[\"STATUS_CHANGED\", \"TASK_COMPLETED\"]")
  private List<RequestActivityEventType> eventTypes;

  @Schema(description = "Optional lower bound (inclusive) for event timestamp")
  private Instant createdAtFrom;

  @Schema(description = "Optional upper bound (inclusive) for event timestamp")
  private Instant createdAtTo;

  /** Returns event type names for SQL filtering. */
  public List<String> getEventTypeNames() {
    if (eventTypes == null || eventTypes.isEmpty()) {
      return null;
    }
    return eventTypes.stream().map(Enum::name).collect(Collectors.toList());
  }
}
