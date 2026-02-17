package com.gurch.sandbox.requests;

import com.gurch.sandbox.dto.CreateResponse;
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

/** REST controller for request CRUD and search endpoints. */
@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

  private final RequestApi requestApi;

  /** Creates a draft request record. */
  @PostMapping("/drafts")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse createDraft(@Valid @RequestBody RequestDtos.CreateDraftRequest request) {
    return new CreateResponse(requestApi.createDraft(request.getName()).getId());
  }

  /** Creates and submits a request record. */
  @PostMapping("/submit")
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse submitNew(@Valid @RequestBody RequestDtos.SubmitRequest request) {
    return new CreateResponse(requestApi.createAndSubmit(request.getName()).getId());
  }

  /** Gets one request by ID. */
  @GetMapping("/{id}")
  public RequestResponse getById(@PathVariable Long id) {
    return requestApi
        .findById(id)
        .orElseThrow(() -> new com.gurch.sandbox.web.NotFoundException("Request not found"));
  }

  /** Updates an existing draft request record. */
  @PutMapping("/{id}")
  @ApiErrorEnum({RequestDraftErrorCode.class})
  public RequestResponse updateDraft(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateDraftRequest request) {
    return requestApi.updateDraft(id, request.getName(), request.getVersion());
  }

  /** Submits an existing draft request. */
  @PostMapping("/{id}/submit")
  @ResponseStatus(HttpStatus.OK)
  @ApiErrorEnum({RequestDraftErrorCode.class})
  public RequestResponse submitDraft(
      @PathVariable Long id,
      @Valid @RequestBody(required = false) RequestDtos.UpdateDraftRequest request) {
    if (request == null) {
      return requestApi.submitDraft(id, null, null);
    }
    return requestApi.submitDraft(id, request.getName(), request.getVersion());
  }

  /** Deletes a request by ID. */
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

  /** Searches requests using optional filters and pagination. */
  @GetMapping("/search")
  public RequestDtos.SearchResponse search(RequestSearchCriteria criteria) {
    return new RequestDtos.SearchResponse(requestApi.search(criteria));
  }
}
