package com.gurch.sandbox.forms;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Value;

/** DTO wrappers for forms-file HTTP endpoints. */
public interface FormFileDtos {

  /** Response wrapper returned by form-file search endpoint. */
  @Value
  @Schema(description = "Response wrapper for form-file search")
  class SearchResponse {
    @Schema(description = "Matching form-file records")
    List<FormFileResponse> files;
  }
}
