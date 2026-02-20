package com.gurch.sandbox.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/** Base class for all search criteria. Handles common pagination parameters. */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class SearchCriteria {

  @Min(0)
  @Schema(description = "Zero-indexed page number", example = "0", defaultValue = "0")
  private Integer page;

  @Min(1)
  @Max(100)
  @Schema(description = "Number of records per page", example = "25", defaultValue = "25")
  private Integer size;

  /**
   * Returns the page number, defaulting to 0 if null.
   *
   * @return the page number
   */
  public int getPage() {
    return page == null ? 0 : page;
  }

  /**
   * Returns the page size, defaulting to 25 if null.
   *
   * @return the page size
   */
  public int getSize() {
    return size == null ? 25 : size;
  }
}
