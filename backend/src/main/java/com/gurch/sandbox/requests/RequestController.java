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

  /** Creates a request record. */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @ApiErrorEnum({RequestCreateErrorCode.class})
  public CreateResponse create(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(requestApi.create(request.getName(), request.getStatus()).getId());
  }

  /** Gets one request by ID. */
  @GetMapping("/{id}")
  public RequestResponse getById(@PathVariable Long id) {
    return requestApi
        .findById(id)
        .orElseThrow(() -> new com.gurch.sandbox.web.NotFoundException("Request not found"));
  }

  /** Updates an existing request record. */
  @PutMapping("/{id}")
  @ApiErrorEnum({RequestUpdateErrorCode.class})
  public RequestResponse update(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateRequest request) {
    return requestApi.update(id, request.getName(), request.getStatus(), request.getVersion());
  }

  /** Deletes a request by ID. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    requestApi.deleteById(id);
  }

  /** Searches requests using optional filters and pagination. */
  @GetMapping("/search")
  public RequestDtos.SearchResponse search(RequestSearchCriteria criteria) {
    return new RequestDtos.SearchResponse(requestApi.search(criteria));
  }
}
