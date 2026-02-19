package com.gurch.sandbox.requests;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requests.internal.RequestAuthorization;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
  private final RequestAuthorization requestAuthorization;

  @PostMapping("/drafts")
  @ApiErrorEnum({RequestTypeResolutionErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@requestAuthorization.canWriteRequests(authentication)")
  public CreateResponse createDraft(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(
        requestApi.createDraft(
            CreateRequestCommand.builder()
                .requestTypeKey(request.getRequestTypeKey())
                .payload(request.getPayload())
                .build()));
  }

  @PutMapping("/{id}")
  @ApiErrorEnum({RequestDraftErrorCode.class})
  @PreAuthorize("@requestAuthorization.canWriteRequests(authentication)")
  public CreateResponse updateDraft(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateDraftRequest request) {
    return new CreateResponse(
        requestApi.updateDraft(id, request.getPayload(), request.getVersion()));
  }

  @PostMapping("/{id}/submit")
  @ApiErrorEnum({
    RequestDraftErrorCode.class,
    RequestSubmissionErrorCode.class,
    RequestTypeResolutionErrorCode.class
  })
  @PreAuthorize("@requestAuthorization.canWriteRequests(authentication)")
  public RequestResponse submitDraft(@PathVariable Long id) {
    return requestApi.submitDraft(id);
  }

  @PostMapping
  @ApiErrorEnum({RequestSubmissionErrorCode.class, RequestTypeResolutionErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("@requestAuthorization.canWriteRequests(authentication)")
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

  @GetMapping("/{id}")
  @PreAuthorize("@requestAuthorization.canReadRequests(authentication)")
  public RequestResponse getById(@PathVariable Long id, Authentication authentication) {
    RequestResponse response =
        requestApi.findById(id).orElseThrow(() -> new NotFoundException("Request not found"));
    if (!requestAuthorization.canAccessBusinessClient(
        authentication, response.getBusinessClientId())) {
      throw new AccessDeniedException("Request outside principal client scope");
    }
    return response;
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@requestAuthorization.canWriteRequests(authentication)")
  public void delete(@PathVariable Long id) {
    requestApi.deleteById(id);
  }

  @PostMapping("/{requestId}/tasks/{taskId}/complete")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("@requestAuthorization.canCompleteTask(authentication, #requestId)")
  public void completeTask(
      @PathVariable Long requestId,
      @PathVariable Long taskId,
      @Valid @RequestBody RequestDtos.CompleteTaskRequest request) {
    requestApi.completeTask(requestId, taskId, request.getAction(), request.getComment());
  }

  @GetMapping("/search")
  @PreAuthorize("@requestAuthorization.canSearchRequests(authentication, #criteria)")
  public RequestDtos.SearchResponse search(RequestSearchCriteria criteria) {
    return new RequestDtos.SearchResponse(requestApi.search(criteria));
  }
}
