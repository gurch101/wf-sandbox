package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.RequestTypeErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
import com.gurch.sandbox.web.ValidationErrorException;
import com.gurch.sandbox.workflows.WorkflowApi;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRequestTypeService implements RequestTypeApi {

  private final RequestTypeRepository requestTypeRepository;
  private final RequestTypeVersionRepository versionRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final WorkflowApi workflowApi;

  @Override
  @Transactional(readOnly = true)
  public ResolvedRequestTypeVersion resolveLatestActive(String typeKey) {
    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    Long activeVersionId = type.getActiveVersionId();
    if (activeVersionId == null) {
      throw ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND);
    }

    RequestTypeVersionEntity active =
        versionRepository
            .findById(activeVersionId)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    return toResolved(type.getTypeKey(), active);
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
                .version(1)
                .payloadHandlerId(command.getPayloadHandlerId())
                .processDefinitionKey(command.getProcessDefinitionKey())
                .build());

    requestTypeRepository.save(type.toBuilder().activeVersionId(version.getId()).build());

    return toResolved(type.getTypeKey(), version);
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

    RequestTypeVersionEntity newVersion =
        versionRepository.save(
            RequestTypeVersionEntity.builder()
                .requestTypeId(type.getId())
                .version(previous.getVersion() + 1)
                .payloadHandlerId(command.getPayloadHandlerId())
                .processDefinitionKey(command.getProcessDefinitionKey())
                .build());

    requestTypeRepository.save(
        type.toBuilder()
            .name(command.getName())
            .description(command.getDescription())
            .activeVersionId(newVersion.getId())
            .active(true)
            .build());

    return toResolved(type.getTypeKey(), newVersion);
  }

  @Override
  @Transactional(readOnly = true)
  public List<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select(
                "rt.type_key, rt.name, rt.description, rt.active, "
                    + "rtv.version AS active_version, rtv.payload_handler_id, "
                    + "rtv.process_definition_key")
            .from("request_types", "rt")
            .join(JoinType.INNER, "request_type_versions", "rtv", "rtv.id = rt.active_version_id")
            .where("upper(rt.type_key)", Operator.LIKE, criteria.getTypeKeyPattern())
            .where("rt.active", Operator.EQ, criteria.getActive());

    var query = builder.build();
    return jdbcTemplate.query(
        query.sql(),
        query.params(),
        (rs, rowNum) ->
            RequestTypeSearchResponse.builder()
                .typeKey(rs.getString("type_key"))
                .name(rs.getString("name"))
                .description(rs.getString("description"))
                .active(rs.getBoolean("active"))
                .activeVersion((Integer) rs.getObject("active_version"))
                .payloadHandlerId(rs.getString("payload_handler_id"))
                .processDefinitionKey(rs.getString("process_definition_key"))
                .build());
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
            java.util.Map.of("typeKey", typeKey),
            Integer.class);
    if (usageCount != null && usageCount > 0) {
      throw ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_IN_USE);
    }

    requestTypeRepository.deleteById(type.getId());
  }

  private void validateProcessDefinitionKey(String processDefinitionKey) {
    if (!workflowApi.processDefinitionExists(processDefinitionKey)) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_PROCESS_DEFINITION_KEY);
    }
  }

  private ResolvedRequestTypeVersion toResolved(String typeKey, RequestTypeVersionEntity version) {
    return ResolvedRequestTypeVersion.builder()
        .typeKey(typeKey)
        .version(version.getVersion())
        .payloadHandlerId(version.getPayloadHandlerId())
        .processDefinitionKey(version.getProcessDefinitionKey())
        .build();
  }
}
