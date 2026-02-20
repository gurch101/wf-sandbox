package com.gurch.sandbox.tenants.internal;

import com.gurch.sandbox.persistence.PersistenceExceptionUtils;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.TenantCommand;
import com.gurch.sandbox.tenants.TenantErrorCode;
import com.gurch.sandbox.tenants.TenantResponse;
import com.gurch.sandbox.tenants.TenantSearchCriteria;
import com.gurch.sandbox.tenants.TenantSearchResponse;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultTenantService implements TenantApi {

  private final TenantRepository repository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  @Transactional(readOnly = true)
  public Optional<TenantResponse> findById(Integer id) {
    return repository.findById(id).map(this::toResponse);
  }

  @Override
  @Transactional
  public Integer create(TenantCommand command) {
    try {
      TenantEntity created =
          repository.save(
              TenantEntity.builder()
                  .name(command.getName().trim())
                  .active(Optional.ofNullable(command.getActive()).orElse(true))
                  .build());
      return created.getId();
    } catch (RuntimeException ex) {
      throw mapPersistenceFailure(ex);
    }
  }

  @Override
  @Transactional
  public Integer update(Integer id, TenantCommand command, Long version) {
    TenantEntity existing =
        repository.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found"));

    try {
      TenantEntity updated =
          repository.save(
              existing.toBuilder()
                  .name(command.getName().trim())
                  .active(Optional.ofNullable(command.getActive()).orElse(existing.isActive()))
                  .version(version)
                  .build());
      return updated.getId();
    } catch (RuntimeException ex) {
      throw mapPersistenceFailure(ex);
    }
  }

  @Override
  @Transactional
  public void deleteById(Integer id) {
    TenantEntity existing =
        repository.findById(id).orElseThrow(() -> new NotFoundException("Tenant not found"));
    try {
      repository.delete(existing);
    } catch (RuntimeException ex) {
      throw mapPersistenceFailure(ex);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public List<TenantSearchResponse> search(TenantSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select(
                "t.id, t.name, t.active, t.created_at AS createdAt, "
                    + "t.updated_at AS updatedAt, t.version")
            .from("tenants", "t")
            .where("upper(t.name)", Operator.LIKE, criteria.getNamePattern())
            .where("t.active", Operator.EQ, criteria.getActive())
            .page(criteria.getPage(), criteria.getSize());

    BuiltQuery query = builder.build();
    return jdbcTemplate.query(
        query.sql(), query.params(), new DataClassRowMapper<>(TenantSearchResponse.class));
  }

  private static RuntimeException mapPersistenceFailure(RuntimeException ex) {
    String details = PersistenceExceptionUtils.fullMessage(ex).toLowerCase(Locale.ROOT);
    if (details.contains("tenants_name_key")) {
      return ValidationErrorException.of(TenantErrorCode.TENANT_NAME_ALREADY_EXISTS);
    }
    if (details.contains("fk_users_tenant_id")) {
      return ValidationErrorException.of(TenantErrorCode.TENANT_IN_USE);
    }
    return ex;
  }

  private TenantResponse toResponse(TenantEntity entity) {
    return TenantResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .active(entity.isActive())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }
}
