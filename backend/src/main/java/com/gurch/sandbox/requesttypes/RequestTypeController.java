package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.web.ApiErrorEnum;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Internal controller for request type lifecycle management. */
@RestController
@RequestMapping("/api/internal/request-types")
@RequiredArgsConstructor
public class RequestTypeController {

  private final RequestTypeApi requestTypeApi;

  /** Creates a new request type with version 1 active. */
  @PostMapping
  @ApiErrorEnum({RequestTypeErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public ResolvedRequestTypeVersion create(
      @Valid @RequestBody RequestTypeDtos.CreateTypeRequest req) {
    return requestTypeApi.createType(
        RequestTypeCommand.builder()
            .typeKey(req.getTypeKey())
            .name(req.getName())
            .description(req.getDescription())
            .payloadHandlerId(req.getPayloadHandlerId())
            .processDefinitionKey(req.getProcessDefinitionKey())
            .build());
  }

  /** Changes an existing request type by appending and activating a new version. */
  @PutMapping("/{typeKey}")
  @ApiErrorEnum({RequestTypeErrorCode.class})
  public ResolvedRequestTypeVersion change(
      @PathVariable String typeKey, @Valid @RequestBody RequestTypeDtos.ChangeTypeRequest req) {
    return requestTypeApi.changeType(
        typeKey,
        RequestTypeCommand.builder()
            .typeKey(typeKey)
            .name(req.getName())
            .description(req.getDescription())
            .payloadHandlerId(req.getPayloadHandlerId())
            .processDefinitionKey(req.getProcessDefinitionKey())
            .build());
  }

  /** Searches request types with optional filters. */
  @GetMapping("/search")
  public List<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria) {
    return requestTypeApi.search(criteria);
  }

  /** Deletes a request type when it is not used by any request. */
  @DeleteMapping("/{typeKey}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiErrorEnum({RequestTypeErrorCode.class})
  public void delete(@PathVariable String typeKey) {
    requestTypeApi.deleteType(typeKey);
  }
}
