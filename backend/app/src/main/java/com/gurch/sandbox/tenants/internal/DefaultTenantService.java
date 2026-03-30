package com.gurch.sandbox.tenants.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.persistence.PersistenceExceptionUtils;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.tenants.TenantApi;
import com.gurch.sandbox.tenants.TenantErrorCode;
import com.gurch.sandbox.tenants.dto.TenantCommand;
import com.gurch.sandbox.tenants.dto.TenantResponse;
import com.gurch.sandbox.tenants.dto.TenantSearchCriteria;
import com.gurch.sandbox.tenants.dto.TenantSearchResponse;
import com.gurch.sandbox.tenants.internal.models.TenantEntity;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultTenantService implements TenantApi {

  private static final String TENANTS_RESOURCE_TYPE = "tenants";

  private final TenantRepository repository;
  private final SearchExecutor searchExecutor;
  private final AuditLogApi auditLogApi;

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
      auditLogApi.recordCreate(TENANTS_RESOURCE_TYPE, created.getId(), created);
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
      TenantEntity beforeState = existing;
      TenantEntity updated =
          repository.save(
              existing.toBuilder()
                  .name(command.getName().trim())
                  .active(Optional.ofNullable(command.getActive()).orElse(existing.isActive()))
                  .version(version)
                  .build());
      auditLogApi.recordUpdate(TENANTS_RESOURCE_TYPE, updated.getId(), beforeState, updated);
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
      auditLogApi.recordDelete(TENANTS_RESOURCE_TYPE, id, existing);
    } catch (RuntimeException ex) {
      throw mapPersistenceFailure(ex);
    }
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<TenantSearchResponse> search(TenantSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.newBuilder()
            .select(
                "t.id, t.name, t.active, t.created_at AS createdAt, "
                    + "t.updated_at AS updatedAt, t.version")
            .from("tenants", "t")
            .where("upper(t.name)", Operator.LIKE, criteria.getNamePattern())
            .where("t.active", Operator.EQ, criteria.getActive());

    return searchExecutor.execute(builder, criteria, TenantSearchResponse.class);
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
