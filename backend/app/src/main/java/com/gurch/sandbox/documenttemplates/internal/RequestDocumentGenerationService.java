package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.requests.RequestApi;
import com.gurch.sandbox.requests.RequestResponse;
import com.gurch.sandbox.requesttypes.RequestTypeApi;
import com.gurch.sandbox.requesttypes.ResolvedRequestTypeVersion;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StorageApi;
import com.gurch.sandbox.web.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Resolves request-driven template mappings and renders one merged PDF output. */
@Service
@RequiredArgsConstructor
public class RequestDocumentGenerationService {

  private final RequestApi requestApi;
  private final RequestTypeApi requestTypeApi;
  private final DocumentTemplateRepository templateRepository;
  private final StorageApi storageApi;
  private final DocumentTemplateGenerationService generationService;
  private final CurrentUserProvider currentUserProvider;
  private final ObjectMapper objectMapper;

  public byte[] generateForRequestIds(List<Long> requestIds) {
    Integer currentTenantId = currentUserProvider.currentTenantId().orElse(null);
    List<DocumentTemplateGenerationService.TemplateRenderSource> renderSources = new ArrayList<>();
    for (Long requestId : requestIds) {
      if (requestId == null) {
        throw new IllegalArgumentException("requestIds cannot contain null values");
      }
      RequestResponse request =
          requestApi
              .findById(requestId)
              .orElseThrow(() -> new NotFoundException("Request not found with id: " + requestId));
      ResolvedRequestTypeVersion resolvedType =
          request.getRequestTypeVersion() == null
              ? requestTypeApi.resolveLatestActive(request.getRequestTypeKey())
              : requestTypeApi.resolveVersion(
                  request.getRequestTypeKey(), request.getRequestTypeVersion());
      List<DocumentMapping> mappings =
          parseMappings(resolvedType.getConfigJson(), requestId, currentTenantId);
      if (mappings.isEmpty()) {
        throw new IllegalArgumentException(
            "No document generation mappings configured for request id: " + requestId);
      }
      for (DocumentMapping mapping : mappings) {
        Optional<DocumentTemplateEntity> resolvedTemplate =
            resolveTemplate(mapping.templateKey(), currentTenantId);
        if (resolvedTemplate.isEmpty()) {
          if (!mapping.required()) {
            continue;
          }
          throw new IllegalArgumentException(
              "No template found for key '"
                  + mapping.templateKey()
                  + "' on request id "
                  + requestId);
        }
        DocumentTemplateEntity template = resolvedTemplate.orElseThrow();
        Map<String, Object> fields =
            resolveFields(
                mapping.fieldBindings(), request.getPayload(), requestId, mapping.templateKey());
        validateTemplateFields(template, fields.keySet(), mapping.templateKey());
        renderSources.add(
            new DocumentTemplateGenerationService.TemplateRenderSource(
                template.getMimeType(), readStoredBytes(template.getStoragePath()), fields));
      }
    }
    return generationService.generateComposedPdf(renderSources);
  }

  private List<DocumentMapping> parseMappings(
      JsonNode configJson, Long requestId, Integer tenantId) {
    if (configJson == null || configJson.isNull()) {
      return List.of();
    }
    JsonNode root = configJson.path("documentGeneration").path("documents");
    if (!root.isArray()) {
      return List.of();
    }
    List<DocumentMapping> mappings = new ArrayList<>();
    for (JsonNode node : root) {
      if (!node.isObject()) {
        throw new IllegalArgumentException(
            "Invalid document mapping entry for request id: " + requestId);
      }
      String templateKey = trimToNull(node.path("templateKey").asText(null));
      if (templateKey == null) {
        throw new IllegalArgumentException(
            "Document mapping templateKey is required for request id: " + requestId);
      }
      JsonNode bindingsNode = node.path("fieldBindings");
      if (!bindingsNode.isObject()) {
        throw new IllegalArgumentException(
            "Document mapping fieldBindings must be an object for templateKey: " + templateKey);
      }
      Map<String, String> bindings = new LinkedHashMap<>();
      bindingsNode
          .fields()
          .forEachRemaining(
              entry -> {
                String fieldKey = trimToNull(entry.getKey());
                String payloadPath =
                    entry.getValue().isTextual() ? trimToNull(entry.getValue().asText()) : null;
                if (fieldKey == null || payloadPath == null) {
                  throw new IllegalArgumentException(
                      "Field bindings must map field key to payload path for templateKey: "
                          + templateKey);
                }
                bindings.put(fieldKey, payloadPath);
              });
      boolean required = resolveRequired(node, tenantId);
      boolean enabled = resolveEnabled(node, tenantId);
      if (!enabled) {
        continue;
      }
      mappings.add(new DocumentMapping(templateKey, bindings, required));
    }
    return mappings;
  }

  private static boolean resolveRequired(JsonNode documentNode, Integer tenantId) {
    boolean required =
        !documentNode.has("required") || documentNode.path("required").asBoolean(true);
    JsonNode tenantRules = documentNode.path("tenantRules");
    if (!tenantRules.isArray() || tenantId == null) {
      return required;
    }
    for (JsonNode tenantRule : tenantRules) {
      if (!tenantRule.isObject()) {
        continue;
      }
      JsonNode tenantIdNode = tenantRule.path("tenantId");
      if (!tenantIdNode.canConvertToInt()) {
        continue;
      }
      if (!tenantId.equals(tenantIdNode.asInt())) {
        continue;
      }
      if (tenantRule.has("required")) {
        return tenantRule.path("required").asBoolean(required);
      }
      return required;
    }
    return required;
  }

  private static boolean resolveEnabled(JsonNode documentNode, Integer tenantId) {
    boolean enabled = !documentNode.has("enabled") || documentNode.path("enabled").asBoolean(true);
    JsonNode tenantRules = documentNode.path("tenantRules");
    if (!tenantRules.isArray() || tenantId == null) {
      return enabled;
    }
    for (JsonNode tenantRule : tenantRules) {
      if (!tenantRule.isObject()) {
        continue;
      }
      JsonNode tenantIdNode = tenantRule.path("tenantId");
      if (!tenantIdNode.canConvertToInt()) {
        continue;
      }
      if (!tenantId.equals(tenantIdNode.asInt())) {
        continue;
      }
      if (tenantRule.has("enabled")) {
        return tenantRule.path("enabled").asBoolean(enabled);
      }
      return enabled;
    }
    return enabled;
  }

  private Optional<DocumentTemplateEntity> resolveTemplate(String templateKey, Integer tenantId) {
    if (tenantId != null) {
      Optional<DocumentTemplateEntity> tenantTemplate =
          templateRepository.findByTenantIdAndTemplateKey(tenantId, templateKey);
      if (tenantTemplate.isPresent()) {
        return tenantTemplate;
      }
      return templateRepository.findByTenantIdIsNullAndTemplateKey(templateKey);
    }

    Optional<DocumentTemplateEntity> global =
        templateRepository.findByTenantIdIsNullAndTemplateKey(templateKey);
    if (global.isPresent()) {
      return global;
    }
    List<DocumentTemplateEntity> allMatching = templateRepository.findAllByTemplateKey(templateKey);
    if (allMatching.size() == 1) {
      return Optional.of(allMatching.get(0));
    }
    if (allMatching.size() > 1) {
      throw new IllegalArgumentException(
          "Multiple tenant templates found for key '" + templateKey + "' with no tenant context");
    }
    return Optional.empty();
  }

  private Map<String, Object> resolveFields(
      Map<String, String> bindings, JsonNode payload, Long requestId, String templateKey) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (Map.Entry<String, String> binding : bindings.entrySet()) {
      JsonNode resolvedValue = resolvePayloadPath(payload, binding.getValue());
      if (resolvedValue == null || resolvedValue.isMissingNode() || resolvedValue.isNull()) {
        throw new IllegalArgumentException(
            "Missing required payload path '"
                + binding.getValue()
                + "' for request id "
                + requestId
                + ", templateKey '"
                + templateKey
                + "'");
      }
      if (resolvedValue.isContainerNode()) {
        throw new IllegalArgumentException(
            "Payload path '"
                + binding.getValue()
                + "' resolved to non-scalar value for request id "
                + requestId
                + ", templateKey '"
                + templateKey
                + "'");
      }
      fields.put(binding.getKey(), scalarValue(resolvedValue));
    }
    return fields;
  }

  private void validateTemplateFields(
      DocumentTemplateEntity template, java.util.Set<String> mappedFieldKeys, String templateKey) {
    String formMapJson = template.getFormMapJson();
    if (formMapJson == null || formMapJson.isBlank() || mappedFieldKeys.isEmpty()) {
      return;
    }
    JsonNode root;
    try {
      root = objectMapper.readTree(formMapJson);
    } catch (IOException e) {
      throw new IllegalStateException("Stored template form map is invalid JSON", e);
    }
    JsonNode fields = root.path("fields");
    if (!fields.isArray() || fields.isEmpty()) {
      return;
    }
    Map<String, Boolean> available = new LinkedHashMap<>();
    for (JsonNode field : fields) {
      String key = trimToNull(field.path("key").asText(null));
      if (key != null) {
        available.put(key, Boolean.TRUE);
      }
    }
    for (String mappedKey : mappedFieldKeys) {
      if (!available.containsKey(mappedKey)) {
        throw new IllegalArgumentException(
            "Mapped field key '"
                + mappedKey
                + "' does not exist in templateKey '"
                + templateKey
                + "'");
      }
    }
  }

  private byte[] readStoredBytes(String storagePath) {
    try (InputStream inputStream = storageApi.read(storagePath)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored file", e);
    }
  }

  private static JsonNode resolvePayloadPath(JsonNode payload, String path) {
    if (payload == null) {
      return null;
    }
    String normalized = path.trim();
    if (normalized.startsWith("payload.")) {
      normalized = normalized.substring("payload.".length());
    } else if (normalized.equals("payload")) {
      return payload;
    }
    JsonNode current = payload;
    for (String segment : splitPathSegments(normalized)) {
      if (segment.isBlank()) {
        return null;
      }
      if (current == null) {
        return null;
      }
      if (current.isArray()) {
        if (!segment.chars().allMatch(Character::isDigit)) {
          return null;
        }
        int index = Integer.parseInt(segment);
        if (index < 0 || index >= current.size()) {
          return null;
        }
        current = current.get(index);
        continue;
      }
      if (!current.isObject()) {
        return null;
      }
      current = current.get(segment);
    }
    return current;
  }

  private static List<String> splitPathSegments(String path) {
    List<String> segments = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < path.length(); i++) {
      if (path.charAt(i) == '.') {
        segments.add(path.substring(start, i));
        start = i + 1;
      }
    }
    segments.add(path.substring(start));
    return segments;
  }

  private static Object scalarValue(JsonNode value) {
    if (value.isTextual()) {
      return value.asText();
    }
    if (value.isBoolean()) {
      return value.booleanValue();
    }
    if (value.isIntegralNumber()) {
      return value.longValue();
    }
    if (value.isFloatingPointNumber()) {
      return BigDecimal.valueOf(value.doubleValue());
    }
    return value.asText();
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private record DocumentMapping(
      String templateKey, Map<String, String> fieldBindings, boolean required) {}
}
