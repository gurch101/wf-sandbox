package com.gurch.sandbox.requests;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requesttypes.RequestTypeErrorCode;
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

/** REST controller for request create/read/search operations. */
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

  private final RequestApi requestApi;

  /** Creates a new draft request without workflow start. */
  @PostMapping("/drafts")
  @ApiErrorEnum({RequestTypeErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse createDraft(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey(request.getRequestTypeKey())
                .payload(request.getPayload())
                .build()));
  }

  /** Updates an existing draft request. */
  @PutMapping("/{id}")
  @ApiErrorEnum({RequestDraftErrorCode.class})
  public CreateResponse updateDraft(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateDraftRequest request) {
    return new CreateResponse(
        requestApi.updateDraft(id, request.getPayload(), request.getVersion()));
  }

  /** Submits a draft request and starts workflow. */
  @PostMapping("/{id}/submit")
  @ApiErrorEnum({
    RequestDraftErrorCode.class,
    RequestSubmissionErrorCode.class,
    RequestTypeErrorCode.class
  })
  public RequestResponse submitDraft(@PathVariable Long id) {
    return requestApi.submitDraft(id);
  }

  /** Creates and submits a new request. */
  @PostMapping
  @ApiErrorEnum({RequestSubmissionErrorCode.class, RequestTypeErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse create(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(
        requestApi
            .createAndSubmit(
                CreateRequestCommand.builder()
                    .requestTypeKey(request.getRequestTypeKey())
                    .payload(request.getPayload())
                    .build())
            .getId());
  }

  /** Returns a request by id. */
  @GetMapping("/{id}")
  public RequestResponse getById(@PathVariable Long id) {
    return requestApi
        .findById(id)
        .orElseThrow(() -> new com.gurch.sandbox.web.NotFoundException("Request not found"));
  }

  /** Deletes a request by id. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    requestApi.deleteById(id);
  }

  /** Completes a request user task. */
  @PostMapping("/{requestId}/tasks/{taskId}/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void completeTask(
      @PathVariable Long requestId,
      @PathVariable Long taskId,
      @Valid @RequestBody RequestDtos.CompleteTaskRequest request) {
    requestApi.completeTask(requestId, taskId, request.getAction(), request.getComment());
  }

  /** Searches requests by optional filters. */
  @GetMapping("/search")
  public RequestDtos.SearchResponse search(RequestSearchCriteria criteria) {
    return new RequestDtos.SearchResponse(requestApi.search(criteria));
  }
}
