package com.gurch.sandbox.users.internal;

import com.gurch.sandbox.persistence.PersistenceExceptionUtils;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.users.UserApi;
import com.gurch.sandbox.users.UserCommand;
import com.gurch.sandbox.users.UserErrorCode;
import com.gurch.sandbox.users.UserResponse;
import com.gurch.sandbox.users.UserSearchCriteria;
import com.gurch.sandbox.users.UserSearchResponse;
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
public class DefaultUserService implements UserApi {

  private final UserRepository repository;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  @Transactional(readOnly = true)
  public Optional<UserResponse> findById(Integer id) {
    return repository.findById(id).map(this::toResponse);
  }

  @Override
  @Transactional
  public Integer create(UserCommand command) {
    try {
      UserEntity created =
          repository.save(
              UserEntity.builder()
                  .username(command.getUsername().trim())
                  .email(command.getEmail().trim())
                  .active(Optional.ofNullable(command.getActive()).orElse(true))
                  .tenantId(command.getTenantId())
                  .build());
      return created.getId();
    } catch (RuntimeException ex) {
      throw mapPersistenceFailure(ex);
    }
  }

  @Override
  @Transactional
  public Integer update(Integer id, UserCommand command, Long version) {
    UserEntity existing =
        repository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));

    try {
      UserEntity updated =
          repository.save(
              existing.toBuilder()
                  .username(existing.getUsername())
                  .email(command.getEmail().trim())
                  .active(Optional.ofNullable(command.getActive()).orElse(existing.isActive()))
                  .tenantId(command.getTenantId())
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
    UserEntity existing =
        repository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    repository.delete(existing);
  }

  @Override
  @Transactional(readOnly = true)
  public List<UserSearchResponse> search(UserSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select(
                "u.id, u.username, u.email, u.active, u.tenant_id AS tenantId, "
                    + "u.created_at AS createdAt, u.updated_at AS updatedAt, u.version")
            .from("users", "u")
            .where("upper(u.username)", Operator.LIKE, criteria.getUsernamePattern())
            .where("upper(u.email)", Operator.LIKE, criteria.getEmailPattern())
            .where("u.active", Operator.EQ, criteria.getActive())
            .where("u.tenant_id", Operator.EQ, criteria.getTenantId())
            .page(criteria.getPage(), criteria.getSize());

    BuiltQuery query = builder.build();
    return jdbcTemplate.query(
        query.sql(), query.params(), new DataClassRowMapper<>(UserSearchResponse.class));
  }

  private static RuntimeException mapPersistenceFailure(RuntimeException ex) {
    String details = PersistenceExceptionUtils.fullMessage(ex).toLowerCase(Locale.ROOT);
    if (details.contains("users_username_key")) {
      return ValidationErrorException.of(UserErrorCode.USERNAME_ALREADY_EXISTS);
    }
    if (details.contains("users_email_key")) {
      return ValidationErrorException.of(UserErrorCode.EMAIL_ALREADY_EXISTS);
    }
    if (details.contains("fk_users_tenant_id")) {
      return ValidationErrorException.of(UserErrorCode.TENANT_NOT_FOUND);
    }
    return ex;
  }

  private UserResponse toResponse(UserEntity entity) {
    return UserResponse.builder()
        .id(entity.getId())
        .username(entity.getUsername())
        .email(entity.getEmail())
        .active(entity.isActive())
        .tenantId(entity.getTenantId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }
}
