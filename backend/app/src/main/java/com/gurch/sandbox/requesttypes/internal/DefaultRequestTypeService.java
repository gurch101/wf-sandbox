package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.requesttypes.dto.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.dto.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.dto.ResolvedRequestTypeVersion;
import com.gurch.sandbox.requesttypes.internal.models.RequestTypeEntity;
import com.gurch.sandbox.requesttypes.internal.models.RequestTypeVersionEntity;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.web.ValidationErrorException;
import com.gurch.sandbox.workflows.WorkflowApi;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRequestTypeService implements RequestTypeApi {

  private static final String REQUEST_TYPES_RESOURCE_TYPE = "request_types";
  private static final String REQUEST_TYPE_VERSIONS_RESOURCE_TYPE = "request_type_versions";

  private final RequestTypeRepository requestTypeRepository;
  private final RequestTypeVersionRepository versionRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final WorkflowApi workflowApi;
  private final SearchExecutor searchExecutor;
  private final AuditLogApi auditLogApi;

  @Override
  @Transactional(readOnly = true)
  public ResolvedRequestTypeVersion resolveLatestActive(String typeKey) {

    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    Long activeVersionId = type.getActiveVersionId();
    if (activeVersionId == null) {
      throw ValidationErrorException.of(RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND);
    }

    RequestTypeVersionEntity active =
        versionRepository
            .findById(activeVersionId)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    return toResolved(type.getTypeKey(), active);
  }

  @Override
  @Transactional(readOnly = true)
  public ResolvedRequestTypeVersion resolveVersion(String typeKey, Integer version) {
    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    RequestTypeVersionEntity resolvedVersion =
        versionRepository
            .findByRequestTypeIdAndTypeVersion(type.getId(), version)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    return toResolved(type.getTypeKey(), resolvedVersion);
  }

  @Override
  @Transactional
  public ResolvedRequestTypeVersion createType(RequestTypeCommand command) {
    validateProcessDefinitionKey(command.getProcessDefinitionKey());

    RequestTypeEntity type =
        requestTypeRepository.save(
            RequestTypeEntity.builder()
                .typeKey(command.getTypeKey())
                .name(command.getName())
                .description(command.getDescription())
                .active(true)
                .build());

    RequestTypeVersionEntity version =
        versionRepository.save(
            RequestTypeVersionEntity.builder()
                .requestTypeId(type.getId())
                .typeVersion(1)
                .processDefinitionKey(command.getProcessDefinitionKey())
                .build());

    RequestTypeEntity updatedType =
        requestTypeRepository.save(type.toBuilder().activeVersionId(version.getId()).build());
    auditLogApi.recordCreate(REQUEST_TYPES_RESOURCE_TYPE, updatedType.getId(), updatedType);
    auditLogApi.recordCreate(REQUEST_TYPE_VERSIONS_RESOURCE_TYPE, version.getId(), version);

    return toResolved(updatedType.getTypeKey(), version);
  }

  @Override
  @Transactional
  public ResolvedRequestTypeVersion changeType(String typeKey, RequestTypeCommand command) {
    validateProcessDefinitionKey(command.getProcessDefinitionKey());

    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    Long activeVersionId = type.getActiveVersionId();
    if (activeVersionId == null) {
      throw ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND);
    }

    RequestTypeVersionEntity previous =
        versionRepository
            .findById(activeVersionId)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));
    RequestTypeEntity beforeTypeState = type;

    RequestTypeVersionEntity newVersion =
        versionRepository.save(
            RequestTypeVersionEntity.builder()
                .requestTypeId(type.getId())
                .typeVersion(previous.getTypeVersion() + 1)
                .processDefinitionKey(command.getProcessDefinitionKey())
                .build());
    auditLogApi.recordCreate(REQUEST_TYPE_VERSIONS_RESOURCE_TYPE, newVersion.getId(), newVersion);

    RequestTypeEntity updatedType =
        requestTypeRepository.save(
            type.toBuilder()
                .name(command.getName())
                .description(command.getDescription())
                .activeVersionId(newVersion.getId())
                .active(true)
                .build());
    auditLogApi.recordUpdate(
        REQUEST_TYPES_RESOURCE_TYPE, updatedType.getId(), beforeTypeState, updatedType);

    return toResolved(updatedType.getTypeKey(), newVersion);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.newBuilder()
            .select(
                "rt.type_key, rt.name, rt.description, rt.active, "
                    + "rtv.type_version AS active_version, rtv.process_definition_key")
            .from("request_types", "rt")
            .join(JoinType.INNER, "request_type_versions", "rtv", "rtv.id = rt.active_version_id")
            .where("upper(rt.type_key)", Operator.LIKE, criteria.getTypeKeyPattern())
            .where("rt.active", Operator.EQ, criteria.getActive());

    return searchExecutor.execute(builder, criteria, RequestTypeSearchResponse.class);
  }

  @Override
  @Transactional
  public void deleteType(String typeKey) {
    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    Integer usageCount =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM requests WHERE request_type_key = :typeKey",
            Map.of("typeKey", typeKey),
            Integer.class);
    if (usageCount != null && usageCount > 0) {
      throw ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_IN_USE);
    }

    requestTypeRepository.deleteById(type.getId());
    auditLogApi.recordDelete(REQUEST_TYPES_RESOURCE_TYPE, type.getId(), type);
  }

  private void validateProcessDefinitionKey(String processDefinitionKey) {
    if (!workflowApi.processDefinitionExists(processDefinitionKey)) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_PROCESS_DEFINITION_KEY);
    }
  }

  private ResolvedRequestTypeVersion toResolved(String typeKey, RequestTypeVersionEntity version) {
    return ResolvedRequestTypeVersion.builder()
        .typeKey(typeKey)
        .version(version.getTypeVersion())
        .processDefinitionKey(version.getProcessDefinitionKey())
        .build();
  }
}
