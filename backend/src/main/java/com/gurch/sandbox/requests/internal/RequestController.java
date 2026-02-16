package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
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

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
public class RequestController {

  private final RequestApi requestApi;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse create(@Valid @RequestBody RequestDtos.CreateRequest request) {
    return new CreateResponse(requestApi.create(request.getName(), request.getStatus()).getId());
  }

  @GetMapping("/{id}")
  public RequestResponse getById(@PathVariable Long id) {
    return requestApi
        .findById(id)
        .orElseThrow(() -> new com.gurch.sandbox.web.NotFoundException("Request not found"));
  }

  @PutMapping("/{id}")
  public RequestResponse update(
      @PathVariable Long id, @Valid @RequestBody RequestDtos.UpdateRequest request) {
    return requestApi.update(id, request.getName(), request.getStatus(), request.getVersion());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    requestApi.deleteById(id);
  }

  @GetMapping("/search")
  public List<RequestResponse> search(RequestSearchCriteria criteria) {
    return requestApi.search(criteria);
  }
}
