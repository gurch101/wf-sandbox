package com.gurch.sandbox.requesttypes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requesttypes.PayloadHandlerCatalog;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.RequestTypeErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
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
  private final PayloadHandlerCatalog payloadHandlerCatalog;
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
    validatePayloadHandlerId(command.getPayloadHandlerId());
    validateConfigJson(command.getConfigJson());

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
                .payloadHandlerId(command.getPayloadHandlerId())
                .processDefinitionKey(command.getProcessDefinitionKey())
                .configJson(command.getConfigJson())
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
    validatePayloadHandlerId(command.getPayloadHandlerId());
    validateConfigJson(command.getConfigJson());

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
                .payloadHandlerId(command.getPayloadHandlerId())
                .processDefinitionKey(command.getProcessDefinitionKey())
                .configJson(command.getConfigJson())
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
        SQLQueryBuilder.select(
                "rt.type_key, rt.name, rt.description, rt.active, "
                    + "rtv.type_version AS active_version, rtv.payload_handler_id, "
                    + "rtv.process_definition_key")
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

  private void validatePayloadHandlerId(String payloadHandlerId) {
    if (!payloadHandlerCatalog.exists(payloadHandlerId)) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_PAYLOAD_HANDLER_ID);
    }
  }

  private ResolvedRequestTypeVersion toResolved(String typeKey, RequestTypeVersionEntity version) {
    return ResolvedRequestTypeVersion.builder()
        .typeKey(typeKey)
        .version(version.getTypeVersion())
        .payloadHandlerId(version.getPayloadHandlerId())
        .processDefinitionKey(version.getProcessDefinitionKey())
        .configJson(version.getConfigJson())
        .build();
  }

  private void validateConfigJson(JsonNode configJson) {
    if (configJson == null || configJson.isNull()) {
      return;
    }
    if (!configJson.isObject()) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
    }
    JsonNode documentGeneration = configJson.path("documentGeneration");
    if (!documentGeneration.isMissingNode() && !documentGeneration.isObject()) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
    }
    JsonNode documents = documentGeneration.path("documents");
    if (!documents.isMissingNode() && !documents.isArray()) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
    }
    if (!documents.isArray()) {
      return;
    }
    for (JsonNode document : documents) {
      if (!document.isObject()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      JsonNode templateKey = document.path("templateKey");
      if (!templateKey.isTextual() || templateKey.asText().isBlank()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      JsonNode fieldBindings = document.path("fieldBindings");
      if (!fieldBindings.isObject()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      JsonNode required = document.path("required");
      if (!required.isMissingNode() && !required.isBoolean()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      JsonNode enabled = document.path("enabled");
      if (!enabled.isMissingNode() && !enabled.isBoolean()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      JsonNode tenantRules = document.path("tenantRules");
      if (!tenantRules.isMissingNode() && !tenantRules.isArray()) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
      }
      if (tenantRules.isArray()) {
        for (JsonNode tenantRule : tenantRules) {
          if (!tenantRule.isObject()) {
            throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
          }
          JsonNode tenantId = tenantRule.path("tenantId");
          if (!tenantId.canConvertToInt()) {
            throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
          }
          JsonNode tenantRequired = tenantRule.path("required");
          if (!tenantRequired.isMissingNode() && !tenantRequired.isBoolean()) {
            throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
          }
          JsonNode tenantEnabled = tenantRule.path("enabled");
          if (!tenantEnabled.isMissingNode() && !tenantEnabled.isBoolean()) {
            throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
          }
        }
      }
      fieldBindings
          .fields()
          .forEachRemaining(
              entry -> {
                if (entry.getKey() == null || entry.getKey().isBlank()) {
                  throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                }
                JsonNode value = entry.getValue();
                if (value.isTextual()) {
                  if (value.asText().isBlank()) {
                    throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                  }
                  return;
                }
                if (!value.isObject()) {
                  throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                }
                JsonNode pathNode = value.path("path");
                boolean hasPath = pathNode.isTextual() && !pathNode.asText().isBlank();
                boolean hasResolver = value.path("resolver").isTextual();
                boolean hasInputPath = value.path("inputPath").isTextual();
                boolean hasOutputPath = value.path("outputPath").isTextual();
                if (hasPath) {
                  if (hasResolver || hasInputPath || hasOutputPath) {
                    throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                  }
                  return;
                }
                if (!hasResolver || !hasInputPath || !hasOutputPath) {
                  throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                }
                if (value.path("resolver").asText().isBlank()
                    || value.path("inputPath").asText().isBlank()
                    || value.path("outputPath").asText().isBlank()) {
                  throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_CONFIG_JSON);
                }
              });
    }
  }
}
