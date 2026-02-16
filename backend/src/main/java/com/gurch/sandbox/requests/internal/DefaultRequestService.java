package com.gurch.sandbox.requests.internal;

import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requests.RequestSearchCriteria;
import com.gurch.sandbox.requests.RequestStatus;
import com.gurch.sandbox.web.NotFoundException;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRequestService implements RequestApi {

  private final RequestRepository repository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  public List<RequestResponse> findAll() {
    return repository.findAll().stream().map(this::toResponse).toList();
  }

  @Override
  public Optional<RequestResponse> findById(Long id) {
    return repository.findById(id).map(this::toResponse);
  }

  @Override
  @Transactional
  public RequestResponse create(String name, RequestStatus status) {
    RequestEntity entity = RequestEntity.builder().name(name).status(status).build();
    return toResponse(repository.save(entity));
  }

  @Override
  @Transactional
  public RequestResponse update(Long id, String name, RequestStatus status, Long version) {
    return repository
        .findById(id)
        .map(
            existing -> {
              RequestEntity toSave =
                  existing.toBuilder().name(name).status(status).version(version).build();
              return toResponse(repository.save(toSave));
            })
        .orElseThrow(() -> new NotFoundException("Request not found with id: " + id));
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    repository.deleteById(id);
  }

  @Override
  public List<RequestResponse> search(RequestSearchCriteria criteria) {
    List<String> statusStrings =
        Optional.ofNullable(criteria.getStatuses())
            .map(list -> list.stream().map(Enum::name).toList())
            .orElse(null);

    SQLQueryBuilder builder =
        SQLQueryBuilder.select("*")
            .from("requests", "r")
            .where("upper(r.name)", Operator.LIKE, criteria.getNamePattern())
            .where("r.status", Operator.IN, statusStrings)
            .where("r.id", Operator.IN, criteria.getIds());

    if (criteria.getPage() != null && criteria.getSize() != null) {
      builder.page(criteria.getPage(), criteria.getSize());
    }

    BuiltQuery query = builder.build();

    return jdbcTemplate
        .query(query.sql(), query.params(), new DataClassRowMapper<>(RequestEntity.class))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private RequestResponse toResponse(RequestEntity entity) {
    return RequestResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .status(entity.getStatus())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }
}
