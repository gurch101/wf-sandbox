package com.gurch.sandbox.dto;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * A generic response wrapper for paginated search results.
 *
 * @param <T> the type of items in the response
 * @param items the list of results for the current page
 * @param totalElements total number of matching records across all pages
 * @param page zero-indexed current page number
 * @param size number of records per page
 */
@Schema(description = "A generic response wrapper for paginated search results")
public record PagedResponse<T>(
    @Schema(description = "The list of results for the current page") List<T> items,
    @Schema(description = "Total number of matching records across all pages", example = "100")
        long totalElements,
    @Schema(description = "Zero-indexed current page number", example = "0") int page,
    @Schema(description = "Number of records per page", example = "25") int size) {

  /**
   * Compact constructor to defensive copy items.
   *
   * @param items results
   * @param totalElements total
   * @param page page
   * @param size size
   */
  public PagedResponse {
    items = List.copyOf(items == null ? List.of() : items);
  }

  @Override
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP",
      justification = "Defensive copy is made in constructor; List.copyOf is immutable")
  public List<T> items() {
    return items;
  }

  /**
   * Returns the total number of pages.
   *
   * @return total pages
   */
  @Schema(description = "Total number of pages", example = "5")
  public int getTotalPages() {
    return size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
  }
}
