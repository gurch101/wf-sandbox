package com.gurch.sandbox.requesttypes.internal;

import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.JoinType;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.requesttypes.PayloadHandlerCatalog;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.RequestTypeCommand;
import com.gurch.sandbox.requesttypes.RequestTypeErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesApi;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesResponse.ModelerInputField;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.requesttypes.RequestTypeSearchCriteria;
import com.gurch.sandbox.requesttypes.RequestTypeSearchResponse;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.web.ValidationErrorException;
import com.gurch.sandbox.workflows.WorkflowApi;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

@Service
@RequiredArgsConstructor
public class DefaultRequestTypeService implements RequestTypeApi {

  private static final String REQUEST_TYPES_RESOURCE_TYPE = "request_types";
  private static final String REQUEST_TYPE_VERSIONS_RESOURCE_TYPE = "request_type_versions";
  private static final String SEQUENCE_FLOW_CONDITION_REFERENCE_KIND = "SEQUENCE_FLOW_CONDITION";
  private static final Pattern FIELD_REFERENCE_PATTERN =
      Pattern.compile("(request|computed|workflow)\\.[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)*");

  private final RequestTypeRepository requestTypeRepository;
  private final RequestTypeVersionRepository versionRepository;
  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final WorkflowApi workflowApi;
  private final PayloadHandlerCatalog payloadHandlerCatalog;
  private final RequestTypeModelerCapabilitiesApi requestTypeModelerCapabilitiesApi;
  private final WorkflowModelInputReferenceRepository workflowModelInputReferenceRepository;
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
  @Transactional
  public ResolvedRequestTypeVersion publishWorkflowModel(
      String typeKey, Integer version, String bpmnXml) {
    RequestTypeEntity type =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    RequestTypeVersionEntity requestTypeVersion =
        versionRepository
            .findByRequestTypeIdAndTypeVersion(type.getId(), version)
            .orElseThrow(
                () -> ValidationErrorException.of(RequestTypeErrorCode.REQUEST_TYPE_NOT_FOUND));

    CompiledWorkflowModel compiledWorkflowModel = validateWorkflowModel(typeKey, version, bpmnXml);
    String processDefinitionKey;
    try {
      processDefinitionKey =
          workflowApi.deployBpmnModel(typeKey + "-v" + version + ".bpmn", bpmnXml);
    } catch (Exception ex) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_WORKFLOW_MODEL);
    }

    RequestTypeVersionEntity updatedVersion =
        versionRepository.save(
            requestTypeVersion.toBuilder().processDefinitionKey(processDefinitionKey).build());
    workflowModelInputReferenceRepository.deleteByRequestTypeVersionId(requestTypeVersion.getId());
    workflowModelInputReferenceRepository.saveAll(
        compiledWorkflowModel.inputReferences().stream()
            .map(
                reference ->
                    WorkflowModelInputReferenceEntity.builder()
                        .requestTypeVersionId(requestTypeVersion.getId())
                        .processDefinitionKey(processDefinitionKey)
                        .bpmnElementId(reference.bpmnElementId())
                        .referenceKind(reference.referenceKind())
                        .inputKey(reference.inputKey())
                        .build())
            .toList());
    auditLogApi.recordUpdate(
        REQUEST_TYPE_VERSIONS_RESOURCE_TYPE,
        updatedVersion.getId(),
        requestTypeVersion,
        updatedVersion);
    return toResolved(typeKey, updatedVersion);
  }

  @Override
  @Transactional(readOnly = true)
  public PagedResponse<RequestTypeSearchResponse> search(RequestTypeSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.newBuilder()
            .select(
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
    if (StringUtils.hasText(processDefinitionKey)
        && !workflowApi.processDefinitionExists(processDefinitionKey)) {
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
        .build();
  }

  private CompiledWorkflowModel validateWorkflowModel(
      String typeKey, Integer version, String bpmnXml) {
    try {
      Set<String> allowedFields = new HashSet<>();
      for (ModelerInputField field :
          requestTypeModelerCapabilitiesApi
              .getCapabilities(typeKey, version)
              .inputs()
              .payloadFields()) {
        allowedFields.add(field.key());
      }
      for (ModelerInputField field :
          requestTypeModelerCapabilitiesApi
              .getCapabilities(typeKey, version)
              .inputs()
              .computedFields()) {
        allowedFields.add(field.key());
      }
      for (ModelerInputField field :
          requestTypeModelerCapabilitiesApi
              .getCapabilities(typeKey, version)
              .inputs()
              .workflowFields()) {
        allowedFields.add(field.key());
      }

      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      Document document =
          factory.newDocumentBuilder().parse(new InputSource(new StringReader(bpmnXml)));

      NodeList processes = document.getElementsByTagNameNS("*", "process");
      if (processes.getLength() == 0
          || !StringUtils.hasText(
              processes.item(0).getAttributes().getNamedItem("id").getNodeValue())) {
        throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_WORKFLOW_MODEL);
      }

      List<CompiledInputReference> inputReferences = new ArrayList<>();
      NodeList sequenceFlows = document.getElementsByTagNameNS("*", "sequenceFlow");
      for (int i = 0; i < sequenceFlows.getLength(); i++) {
        Element sequenceFlow = (Element) sequenceFlows.item(i);
        NodeList conditions = sequenceFlow.getElementsByTagNameNS("*", "conditionExpression");
        if (conditions.getLength() == 0) {
          continue;
        }

        String bpmnElementId = sequenceFlow.getAttribute("id");
        if (!StringUtils.hasText(bpmnElementId)) {
          throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_WORKFLOW_MODEL);
        }

        Set<String> elementReferences = new LinkedHashSet<>();
        for (int j = 0; j < conditions.getLength(); j++) {
          Matcher matcher = FIELD_REFERENCE_PATTERN.matcher(conditions.item(j).getTextContent());
          while (matcher.find()) {
            String inputKey = matcher.group();
            if (!allowedFields.contains(inputKey)) {
              throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_WORKFLOW_MODEL);
            }
            elementReferences.add(inputKey);
          }
        }

        for (String inputKey : elementReferences) {
          inputReferences.add(
              new CompiledInputReference(
                  bpmnElementId, SEQUENCE_FLOW_CONDITION_REFERENCE_KIND, inputKey));
        }
      }
      return new CompiledWorkflowModel(inputReferences);
    } catch (ValidationErrorException ex) {
      throw ex;
    } catch (IOException | ParserConfigurationException | SAXException ex) {
      throw ValidationErrorException.of(RequestTypeErrorCode.INVALID_WORKFLOW_MODEL);
    }
  }

  private record CompiledWorkflowModel(List<CompiledInputReference> inputReferences) {}

  private record CompiledInputReference(
      String bpmnElementId, String referenceKind, String inputKey) {}
}
