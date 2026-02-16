package com.gurch.sandbox.requests;

import java.util.List;
import java.util.Optional;

public interface RequestApi {
  List<RequestResponse> findAll();

  Optional<RequestResponse> findById(Long id);

  RequestResponse create(String name, RequestStatus status);

  RequestResponse update(Long id, String name, RequestStatus status, Long version);

  void deleteById(Long id);

  List<RequestResponse> search(RequestSearchCriteria criteria);
}
