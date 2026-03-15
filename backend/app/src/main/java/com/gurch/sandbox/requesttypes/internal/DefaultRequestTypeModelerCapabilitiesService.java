package com.gurch.sandbox.requesttypes.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.gurch.sandbox.requesttypes.AssignmentMode;
import com.gurch.sandbox.requesttypes.EscalationHandler;
import com.gurch.sandbox.requesttypes.InputFieldDataType;
import com.gurch.sandbox.requesttypes.ModelerInputProvider;
import com.gurch.sandbox.requesttypes.PayloadHandlerCatalog;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesApi;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesResponse;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesResponse.Inputs;
import com.gurch.sandbox.requesttypes.RequestTypeModelerCapabilitiesResponse.ModelerInputField;
import com.gurch.sandbox.requesttypes.RequestTypeResolutionErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultRequestTypeModelerCapabilitiesService
    implements RequestTypeModelerCapabilitiesApi {

  private static final List<AssignmentMode> ASSIGNMENT_MODES =
      List.of(
          AssignmentMode.CANDIDATE_USERS,
          AssignmentMode.CANDIDATE_GROUPS,
          AssignmentMode.DIRECT_ASSIGNEE,
          AssignmentMode.UNASSIGNED);

  private static final List<EscalationHandler> AVAILABLE_ESCALATION_HANDLERS =
      List.of(
          EscalationHandler.OPS_REROUTE,
          EscalationHandler.SLA_BREACH_MANAGER_ESCALATION,
          EscalationHandler.NOTIFY_REQUEST_OWNER);

  private static final Map<String, List<String>> OPERATORS_BY_TYPE =
      Map.of(
          "STRING",
          List.of(
              "EQ",
              "NEQ",
              "IN",
              "NOT_IN",
              "CONTAINS",
              "STARTS_WITH",
              "ENDS_WITH",
              "IS_NULL",
              "IS_NOT_NULL"),
          "NUMBER",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "BOOLEAN",
          List.of("EQ", "NEQ", "IS_NULL", "IS_NOT_NULL"),
          "DATE",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "DATETIME",
          List.of("EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"),
          "JSON",
          List.of("EQ", "NEQ", "CONTAINS", "IS_NULL", "IS_NOT_NULL"));

  private final RequestTypeRepository requestTypeRepository;
  private final RequestTypeVersionRepository requestTypeVersionRepository;
  private final PayloadHandlerCatalog payloadHandlerCatalog;
  private final List<ModelerInputProvider> inputProviders;

  @Override
  @Transactional(readOnly = true)
  public RequestTypeModelerCapabilitiesResponse getCapabilities(String typeKey, Integer version) {
    RequestTypeEntity requestType =
        requestTypeRepository
            .findByTypeKey(typeKey)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    RequestTypeVersionEntity requestTypeVersion =
        requestTypeVersionRepository
            .findByRequestTypeIdAndTypeVersion(requestType.getId(), version)
            .orElseThrow(
                () ->
                    ValidationErrorException.of(
                        RequestTypeResolutionErrorCode.REQUEST_TYPE_NOT_FOUND));

    List<ModelerInputField> payloadFields =
        derivePayloadFields(requestTypeVersion.getPayloadHandlerId());
    List<ModelerInputField> computedFields = new ArrayList<>();
    List<ModelerInputField> workflowFields = new ArrayList<>();

    for (ModelerInputProvider provider : inputProviders) {
      if (!provider.supports(requestType, requestTypeVersion)) {
        continue;
      }
      computedFields.addAll(provider.computedFields());
      workflowFields.addAll(provider.workflowFields());
    }

    return new RequestTypeModelerCapabilitiesResponse(
        typeKey,
        requestTypeVersion.getTypeVersion(),
        new Inputs(payloadFields, computedFields, workflowFields),
        new LinkedHashMap<>(OPERATORS_BY_TYPE),
        ASSIGNMENT_MODES,
        AVAILABLE_ESCALATION_HANDLERS,
        List.of());
  }

  private List<ModelerInputField> derivePayloadFields(String payloadHandlerId) {
    Class<?> payloadType = payloadHandlerCatalog.payloadType(payloadHandlerId);
    if (JsonNode.class.equals(payloadType)) {
      return List.of();
    }

    List<ModelerInputField> fields = new ArrayList<>();
    for (Field field : payloadType.getDeclaredFields()) {
      fields.add(
          new ModelerInputField(
              "request." + field.getName(),
              toLabel(field.getName()),
              toDataType(field.getType()),
              isRequired(field),
              "$." + field.getName(),
              null,
              List.of(),
              List.of(),
              List.of(),
              "Payload field derived from handler " + payloadHandlerId + "."));
    }
    return fields;
  }

  private boolean isRequired(Field field) {
    return field.getType().isPrimitive()
        || field.isAnnotationPresent(NotNull.class)
        || field.isAnnotationPresent(NotBlank.class)
        || field.isAnnotationPresent(Positive.class);
  }

  private InputFieldDataType toDataType(Class<?> javaType) {
    if (String.class.equals(javaType)) {
      return InputFieldDataType.STRING;
    }
    if (boolean.class.equals(javaType) || Boolean.class.equals(javaType)) {
      return InputFieldDataType.BOOLEAN;
    }
    if (Number.class.isAssignableFrom(javaType)
        || BigDecimal.class.equals(javaType)
        || isPrimitiveNumber(javaType)) {
      return InputFieldDataType.NUMBER;
    }
    if (LocalDate.class.equals(javaType)) {
      return InputFieldDataType.DATE;
    }
    if (Instant.class.equals(javaType) || OffsetDateTime.class.equals(javaType)) {
      return InputFieldDataType.DATETIME;
    }
    return InputFieldDataType.JSON;
  }

  private String toLabel(String fieldName) {
    String spaced = fieldName.replaceAll("([a-z])([A-Z])", "$1 $2");
    return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
  }

  private boolean isPrimitiveNumber(Class<?> javaType) {
    return byte.class.equals(javaType)
        || short.class.equals(javaType)
        || int.class.equals(javaType)
        || long.class.equals(javaType)
        || float.class.equals(javaType)
        || double.class.equals(javaType);
  }
}
