package com.gurch.sandbox.requests;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivityEventResponse;
import com.gurch.sandbox.requests.activity.dto.RequestActivitySearchCriteria;
import com.gurch.sandbox.requests.dto.CreateRequestCommand;
import com.gurch.sandbox.requests.dto.RequestDtos;
import com.gurch.sandbox.requests.dto.RequestResponse;
import com.gurch.sandbox.requests.dto.RequestSearchCriteria;
import com.gurch.sandbox.requests.dto.RequestSearchResponse;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
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
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

  private final RequestApi requestApi;

  @PostMapping("/drafts")
  @ApiErrorEnum({RequestTypeResolutionErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse createDraft(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(
        requestApi.createDraft(
            CreateRequestCommand.builder().requestTypeKey(request.getRequestTypeKey()).build()));
  }

  @PutMapping("/{id}")
  @ApiErrorEnum({RequestDraftErrorCode.class})
  public CreateResponse updateDraft(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateDraftRequest request) {
    return new CreateResponse(requestApi.updateDraft(id, request.getVersion()));
  }

  @PostMapping("/{id}/submit")
  @ApiErrorEnum({RequestDraftErrorCode.class, RequestTypeResolutionErrorCode.class})
  public RequestResponse submitDraft(@PathVariable Long id) {
    return requestApi.submitDraft(id);
  }

  @PostMapping
  @ApiErrorEnum({RequestTypeResolutionErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse create(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(
        requestApi
            .createAndSubmit(
                CreateRequestCommand.builder().requestTypeKey(request.getRequestTypeKey()).build())
            .getId());
  }

  @GetMapping("/{id}")
  public RequestResponse getById(@PathVariable Long id) {
    return requestApi.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
  }

  @GetMapping("/{id}/activity")
  public PagedResponse<RequestActivityEventResponse> activity(
      @PathVariable Long id, RequestActivitySearchCriteria criteria) {
    return requestApi.searchActivity(id, criteria);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    requestApi.deleteById(id);
  }

  @PostMapping("/{requestId}/tasks/{taskId}/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeTask(
      @PathVariable Long requestId,
      @PathVariable Long taskId,
      @Valid @RequestBody RequestDtos.CompleteTaskRequest request) {
    requestApi.completeTask(requestId, taskId, request.getAction(), request.getComment());
  }

  @GetMapping("/search")
  public PagedResponse<RequestSearchResponse> search(RequestSearchCriteria criteria) {
    return requestApi.search(criteria);
  }
}
