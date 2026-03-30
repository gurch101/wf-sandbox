package com.gurch.sandbox.requesttypes;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requesttypes.dto.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.dto.RequestTypeDtos;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.dto.ResolvedRequestTypeVersion;
import com.gurch.sandbox.web.ApiErrorEnum;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/internal/request-types")
@RequiredArgsConstructor
public class RequestTypeController {

  private final RequestTypeApi requestTypeApi;

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
            .processDefinitionKey(req.getProcessDefinitionKey())
            .build());
  }

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
            .processDefinitionKey(req.getProcessDefinitionKey())
            .build());
  }

  @GetMapping("/search")
  public PagedResponse<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria) {
    return requestTypeApi.search(criteria);
  }

  @DeleteMapping("/{typeKey}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @ApiErrorEnum({RequestTypeErrorCode.class})
  public void delete(@PathVariable String typeKey) {
    requestTypeApi.deleteType(typeKey);
  }
}
