package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateDownload;
import com.gurch.sandbox.documenttemplates.DocumentTemplateGenerateFromRequestsRequest;
import com.gurch.sandbox.documenttemplates.DocumentTemplateGenerateRequest;
import com.gurch.sandbox.documenttemplates.DocumentTemplateResponse;
import com.gurch.sandbox.documenttemplates.DocumentTemplateSearchCriteria;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUpdateRequest;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUploadRequest;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StorageApi;
import com.gurch.sandbox.storage.StorageWriteRequest;
import com.gurch.sandbox.storage.StorageWriteResult;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.PayloadTooLargeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultDocumentTemplateService implements DocumentTemplateApi {

  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final String STORAGE_NAMESPACE_DOCUMENT_TEMPLATES = "document-templates";
  private static final String DOCUMENT_TEMPLATES_RESOURCE_TYPE = "documenttemplates";

  private final DocumentTemplateRepository repository;
  private final StorageApi storageApi;
  private final SearchExecutor searchExecutor;
  private final AuditLogApi auditLogApi;
  private final DocumentTemplateIntrospectionService introspectionService;
  private final DocumentTemplateGenerationService generationService;
  private final RequestDocumentGenerationService requestDocumentGenerationService;
  private final ObjectMapper objectMapper;
  private final CurrentUserProvider currentUserProvider;

  @Value("${documenttemplates.upload.max-size-bytes:26214400}")
  private long maxUploadSizeBytes;

  @Override
  @Transactional
  public DocumentTemplateResponse upload(DocumentTemplateUploadRequest request) {
    validateUploadRequest(request);
    validateUploadSize(request.getContentSize());
    validateUploadTenantAccess(request.getTenantId());

    String mimeType = normalizeMimeType(request.getMimeType(), request.getOriginalFilename());
    String displayName = resolveDisplayName(request.getName(), request.getOriginalFilename());
    byte[] payload;
    try {
      payload = request.getContentStream().readAllBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read uploaded file content", e);
    }
    DocumentTemplateIntrospectionService.TemplateIntrospectionResult introspection =
        introspectionService.introspect(mimeType, payload);

    StorageWriteResult stored;
    try {
      stored =
          storageApi.write(
              StorageWriteRequest.builder()
                  .namespace(STORAGE_NAMESPACE_DOCUMENT_TEMPLATES)
                  .originalFilename(request.getOriginalFilename())
                  .contentStream(new ByteArrayInputStream(payload))
                  .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist uploaded file", e);
    }

    DocumentTemplateEntity entity =
        DocumentTemplateEntity.builder()
            .templateKey(trimToNull(request.getTemplateKey()))
            .name(displayName)
            .description(trimToNull(request.getDescription()))
            .mimeType(mimeType)
            .contentSize(stored.contentSize())
            .checksumSha256(stored.checksumSha256())
            .tenantId(request.getTenantId())
            .formMapJson(introspection.formMapJson())
            .esignable(introspection.esignable())
            .storageProvider(stored.provider())
            .storagePath(stored.storagePath())
            .build();

    try {
      DocumentTemplateEntity saved = repository.save(entity);
      auditLogApi.recordCreate(DOCUMENT_TEMPLATES_RESOURCE_TYPE, saved.getId(), saved);
      return toResponse(saved);
    } catch (RuntimeException e) {
      try {
        storageApi.delete(stored.storagePath());
      } catch (IOException ignored) {
        // Intentionally ignored: persistence failure is the root cause.
      }
      throw e;
    }
  }

  @Override
  @Transactional
  public DocumentTemplateResponse update(Long id, DocumentTemplateUpdateRequest request) {
    DocumentTemplateEntity existing =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));
    ensureAccessible(existing, id);

    String updatedName =
        request.getName() == null || request.getName().isBlank()
            ? existing.getName()
            : request.getName().trim();
    String updatedDescription =
        request.getDescription() == null
            ? existing.getDescription()
            : trimToNull(request.getDescription());

    if (!hasReplacementContent(request)) {
      DocumentTemplateEntity beforeState = existing;
      DocumentTemplateEntity updated =
          repository.save(
              existing.toBuilder().name(updatedName).description(updatedDescription).build());
      auditLogApi.recordUpdate(
          DOCUMENT_TEMPLATES_RESOURCE_TYPE, updated.getId(), beforeState, updated);
      return toResponse(updated);
    }

    validateReplacementRequest(request);
    validateUploadSize(request.getContentSize());
    byte[] payload;
    try {
      payload = request.getContentStream().readAllBytes();
    } catch (IOException e) {
      throw new IllegalArgumentException("Could not read uploaded file content", e);
    }
    String replacementMimeType =
        normalizeMimeType(request.getMimeType(), request.getOriginalFilename());
    DocumentTemplateIntrospectionService.TemplateIntrospectionResult introspection =
        introspectionService.introspect(replacementMimeType, payload);
    if (!hasUnchangedFieldMap(existing.getFormMapJson(), introspection.formMapJson())) {
      throw new IllegalArgumentException("Template field map changed and update is not allowed");
    }

    StorageWriteResult stored;
    try {
      stored =
          storageApi.write(
              StorageWriteRequest.builder()
                  .namespace(STORAGE_NAMESPACE_DOCUMENT_TEMPLATES)
                  .originalFilename(request.getOriginalFilename())
                  .contentStream(new ByteArrayInputStream(payload))
                  .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist uploaded file", e);
    }

    DocumentTemplateEntity beforeState = existing;
    DocumentTemplateEntity updatedEntity =
        existing.toBuilder()
            .name(updatedName)
            .description(updatedDescription)
            .mimeType(replacementMimeType)
            .contentSize(stored.contentSize())
            .checksumSha256(stored.checksumSha256())
            .formMapJson(introspection.formMapJson())
            .esignable(introspection.esignable())
            .storageProvider(stored.provider())
            .storagePath(stored.storagePath())
            .build();

    try {
      DocumentTemplateEntity saved = repository.save(updatedEntity);
      auditLogApi.recordUpdate(DOCUMENT_TEMPLATES_RESOURCE_TYPE, saved.getId(), beforeState, saved);
      if (!existing.getStoragePath().equals(stored.storagePath())) {
        try {
          storageApi.delete(existing.getStoragePath());
        } catch (IOException ignored) {
          // Best effort cleanup of previous content.
        }
      }
      return toResponse(saved);
    } catch (RuntimeException e) {
      try {
        storageApi.delete(stored.storagePath());
      } catch (IOException ignored) {
        // Intentionally ignored: persistence failure is the root cause.
      }
      throw e;
    }
  }

  @Override
  public Optional<DocumentTemplateResponse> findById(Long id) {
    Optional<DocumentTemplateEntity> entity = repository.findById(id);
    entity.ifPresent(value -> ensureAccessible(value, id));
    return entity.map(this::toResponse);
  }

  @Override
  public DocumentTemplateDownload download(Long id) {
    DocumentTemplateEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));
    ensureAccessible(entity, id);

    try {
      return DocumentTemplateDownload.builder()
          .name(entity.getName())
          .mimeType(entity.getMimeType())
          .contentSize(entity.getContentSize())
          .contentStream(storageApi.read(entity.getStoragePath()))
          .build();
    } catch (java.nio.file.NoSuchFileException e) {
      throw new NotFoundException("Stored content is missing for document template id: " + id);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored file", e);
    }
  }

  @Override
  public DocumentTemplateDownload generate(DocumentTemplateGenerateRequest request) {
    if (request == null || request.getDocuments() == null || request.getDocuments().isEmpty()) {
      throw new IllegalArgumentException("At least one document is required");
    }

    List<DocumentTemplateGenerationService.TemplateRenderSource> renderSources = new ArrayList<>();
    for (DocumentTemplateGenerateRequest.GenerateInput input : request.getDocuments()) {
      if (input == null || input.getDocumentTemplateId() == null) {
        throw new IllegalArgumentException("Each document must include documentTemplateId");
      }
      DocumentTemplateEntity entity =
          repository
              .findById(input.getDocumentTemplateId())
              .orElseThrow(
                  () ->
                      new NotFoundException(
                          "Document template not found with id: " + input.getDocumentTemplateId()));
      ensureAccessible(entity, input.getDocumentTemplateId());

      byte[] sourceBytes = readStoredBytes(entity.getStoragePath());
      renderSources.add(
          new DocumentTemplateGenerationService.TemplateRenderSource(
              entity.getMimeType(), sourceBytes, input.getFields()));
    }

    byte[] mergedPdf = generationService.generateComposedPdf(renderSources);
    return DocumentTemplateDownload.builder()
        .name("generated-document-bundle.pdf")
        .mimeType(MIME_PDF)
        .contentSize((long) mergedPdf.length)
        .contentStream(new ByteArrayInputStream(mergedPdf))
        .build();
  }

  @Override
  public DocumentTemplateDownload generateFromRequests(
      DocumentTemplateGenerateFromRequestsRequest request) {
    if (request == null || request.getRequestIds() == null || request.getRequestIds().isEmpty()) {
      throw new IllegalArgumentException("At least one request id is required");
    }
    byte[] mergedPdf =
        requestDocumentGenerationService.generateForRequestIds(request.getRequestIds());
    return DocumentTemplateDownload.builder()
        .name("generated-document-bundle.pdf")
        .mimeType(MIME_PDF)
        .contentSize((long) mergedPdf.length)
        .contentStream(new ByteArrayInputStream(mergedPdf))
        .build();
  }

  @Override
  public PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("f.*")
            .from("document_templates", "f")
            .where("upper(f.name)", Operator.LIKE, criteria.getNamePattern());
    Integer userTenantId = currentUserProvider.currentTenantId().orElse(null);
    if (userTenantId != null) {
      builder.rawWhere(
          "(f.tenant_id = :currentTenantId OR f.tenant_id IS NULL)",
          Map.of("currentTenantId", userTenantId));
    }
    if (criteria.getTenantId() != null) {
      builder.where("f.tenant_id", Operator.EQ, criteria.getTenantId());
    }

    PagedResponse<DocumentTemplateEntity> entities =
        searchExecutor.execute(builder, criteria, DocumentTemplateEntity.class);

    return new PagedResponse<>(
        entities.items().stream().map(this::toResponse).toList(),
        entities.totalElements(),
        entities.page(),
        entities.size());
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    DocumentTemplateEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));
    ensureAccessible(entity, id);

    try {
      storageApi.delete(entity.getStoragePath());
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete stored file", e);
    }

    repository.delete(entity);
    auditLogApi.recordDelete(DOCUMENT_TEMPLATES_RESOURCE_TYPE, id, entity);
  }

  private static void validateUploadRequest(DocumentTemplateUploadRequest request) {
    if (request == null || request.getContentSize() == null || request.getContentSize() <= 0) {
      throw new IllegalArgumentException("file is required");
    }
    if (request.getContentStream() == null) {
      throw new IllegalArgumentException("file is required");
    }
    if (request.getOriginalFilename() == null || request.getOriginalFilename().isBlank()) {
      throw new IllegalArgumentException("original filename is required");
    }
  }

  private static void validateReplacementRequest(DocumentTemplateUpdateRequest request) {
    if (request.getContentSize() == null || request.getContentSize() <= 0) {
      throw new IllegalArgumentException("file is required");
    }
    if (request.getContentStream() == null) {
      throw new IllegalArgumentException("file is required");
    }
    if (request.getOriginalFilename() == null || request.getOriginalFilename().isBlank()) {
      throw new IllegalArgumentException("original filename is required");
    }
  }

  private void validateUploadSize(Long contentSize) {
    if (contentSize > maxUploadSizeBytes) {
      throw new PayloadTooLargeException(
          "Uploaded file exceeds max allowed size of " + maxUploadSizeBytes + " bytes");
    }
  }

  private static String normalizeMimeType(String mimeType, String originalFilename) {
    String fileName = originalFilename.toLowerCase(Locale.ROOT);
    if (mimeType != null && !mimeType.isBlank()) {
      String normalized = mimeType.trim().toLowerCase(Locale.ROOT);
      if (MIME_PDF.equals(normalized) || MIME_DOCX.equals(normalized)) {
        return normalized;
      }
      if (fileName.endsWith(".pdf")) {
        return MIME_PDF;
      }
      if (fileName.endsWith(".docx")) {
        return MIME_DOCX;
      }
      throw new IllegalArgumentException("Unsupported file type. Only PDF and DOCX are allowed");
    }
    if (fileName.endsWith(".pdf")) {
      return MIME_PDF;
    }
    if (fileName.endsWith(".docx")) {
      return MIME_DOCX;
    }
    throw new IllegalArgumentException("Unsupported file type. Only PDF and DOCX are allowed");
  }

  private static String resolveDisplayName(String name, String originalFilename) {
    if (name != null && !name.isBlank()) {
      return name.trim();
    }
    return originalFilename.trim();
  }

  private static String trimToNull(String description) {
    if (description == null || description.isBlank()) {
      return null;
    }
    return description.trim();
  }

  private boolean hasUnchangedFieldMap(String previousFormMapJson, String nextFormMapJson) {
    JsonNode previous = parseJsonOrNull(previousFormMapJson);
    JsonNode next = parseJsonOrNull(nextFormMapJson);
    return java.util.Objects.equals(previous, next);
  }

  private JsonNode parseJsonOrNull(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(rawJson);
    } catch (IOException e) {
      throw new IllegalStateException("Stored template form map is invalid JSON", e);
    }
  }

  private DocumentTemplateResponse toResponse(DocumentTemplateEntity entity) {
    return DocumentTemplateResponse.builder()
        .id(entity.getId())
        .templateKey(entity.getTemplateKey())
        .name(entity.getName())
        .description(entity.getDescription())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .checksumSha256(entity.getChecksumSha256())
        .tenantId(entity.getTenantId())
        .formMap(parseFormMap(entity.getFormMapJson()))
        .esignable(entity.isEsignable())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }

  private JsonNode parseFormMap(String formMapJson) {
    if (formMapJson == null || formMapJson.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(formMapJson);
    } catch (IOException e) {
      throw new IllegalStateException("Stored template form map is invalid JSON", e);
    }
  }

  private void validateUploadTenantAccess(Integer requestTenantId) {
    Integer userTenantId = currentUserProvider.currentTenantId().orElse(null);
    if (userTenantId == null) {
      return;
    }
    if (!userTenantId.equals(requestTenantId)) {
      throw new IllegalArgumentException(
          "tenantId must match the authenticated user's tenant scope");
    }
  }

  private void ensureAccessible(DocumentTemplateEntity entity, Long templateId) {
    if (!isAccessibleToCurrentTenant(entity)) {
      throw new NotFoundException("Document template not found with id: " + templateId);
    }
  }

  private boolean isAccessibleToCurrentTenant(DocumentTemplateEntity entity) {
    Integer userTenantId = currentUserProvider.currentTenantId().orElse(null);
    if (userTenantId == null) {
      return true;
    }
    return entity.getTenantId() == null || userTenantId.equals(entity.getTenantId());
  }

  private byte[] readStoredBytes(String storagePath) {
    try (InputStream inputStream = storageApi.read(storagePath)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored file", e);
    }
  }

  private static boolean hasReplacementContent(DocumentTemplateUpdateRequest request) {
    return request != null && request.getContentStream() != null;
  }
}
