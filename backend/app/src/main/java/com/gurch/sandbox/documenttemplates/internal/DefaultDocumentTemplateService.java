package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateDownload;
import com.gurch.sandbox.documenttemplates.DocumentTemplateGenerateErrorCode;
import com.gurch.sandbox.documenttemplates.DocumentTemplateGenerateRequest;
import com.gurch.sandbox.documenttemplates.DocumentTemplateResponse;
import com.gurch.sandbox.documenttemplates.DocumentTemplateSearchCriteria;
import com.gurch.sandbox.documenttemplates.DocumentTemplateSharedErrorCode;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUpdateCommand;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUpdateErrorCode;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUploadCommand;
import com.gurch.sandbox.documenttemplates.DocumentTemplateUploadErrorCode;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.query.WhereClause;
import com.gurch.sandbox.search.SearchExecutor;
import com.gurch.sandbox.security.CurrentUserProvider;
import com.gurch.sandbox.storage.StorageApi;
import com.gurch.sandbox.storage.StorageWriteRequest;
import com.gurch.sandbox.storage.StorageWriteResult;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.PayloadTooLargeException;
import com.gurch.sandbox.web.ValidationErrorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

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
  private final ObjectMapper objectMapper;
  private final CurrentUserProvider currentUserProvider;

  @Value("${documenttemplates.upload.max-size-bytes:26214400}")
  private long maxUploadSizeBytes;

  @Override
  @Transactional
  public DocumentTemplateResponse upload(DocumentTemplateUploadCommand command) {
    validateUploadRequest(command);

    String mimeType = normalizeMimeType(command.getMimeType(), command.getOriginalFilename());
    String trimmedEnName = StringUtils.trimToNull(command.getEnName());
    String enDisplayName =
        trimmedEnName != null ? trimmedEnName : command.getOriginalFilename().trim();
    byte[] payload = readRequestPayload(command.getContentStream());
    TemplateIntrospectionResult introspection = introspectionService.introspect(mimeType, payload);

    StorageWriteResult stored = persistPayload(command.getOriginalFilename(), payload);

    DocumentTemplateEntity entity =
        DocumentTemplateEntity.builder()
            .enName(enDisplayName)
            .frName(StringUtils.trimToNull(command.getFrName()))
            .enDescription(StringUtils.trimToNull(command.getEnDescription()))
            .frDescription(StringUtils.trimToNull(command.getFrDescription()))
            .mimeType(mimeType)
            .contentSize(stored.contentSize())
            .checksumSha256(stored.checksumSha256())
            .tenantId(command.getTenantId())
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
  public DocumentTemplateResponse update(Long id, DocumentTemplateUpdateCommand command) {
    DocumentTemplateEntity existing = loadAccessibleTemplate(id);

    String updatedEnName = coalesceTrimmedRequired(command.getEnName(), existing.getEnName());
    String updatedFrName = coalesceTrimmedNullable(command.getFrName(), existing.getFrName());
    String updatedEnDescription =
        coalesceTrimmedNullable(command.getEnDescription(), existing.getEnDescription());
    String updatedFrDescription =
        coalesceTrimmedNullable(command.getFrDescription(), existing.getFrDescription());

    if (!hasReplacementContent(command)) {
      DocumentTemplateEntity beforeState = existing;
      DocumentTemplateEntity updated =
          repository.save(
              existing.toBuilder()
                  .enName(updatedEnName)
                  .frName(updatedFrName)
                  .enDescription(updatedEnDescription)
                  .frDescription(updatedFrDescription)
                  .build());
      auditLogApi.recordUpdate(
          DOCUMENT_TEMPLATES_RESOURCE_TYPE, updated.getId(), beforeState, updated);
      return toResponse(updated);
    }

    validateReplacementRequest(command);
    byte[] payload = readRequestPayload(command.getContentStream());
    String replacementMimeType =
        normalizeMimeType(command.getMimeType(), command.getOriginalFilename());
    TemplateIntrospectionResult introspection =
        introspectionService.introspect(replacementMimeType, payload);
    if (!hasUnchangedFieldMap(existing.getFormMapJson(), introspection.formMapJson())) {
      throw ValidationErrorException.of(DocumentTemplateUpdateErrorCode.TEMPLATE_FIELD_MAP_CHANGED);
    }

    StorageWriteResult stored = persistPayload(command.getOriginalFilename(), payload);

    DocumentTemplateEntity beforeState = existing;
    DocumentTemplateEntity updatedEntity =
        existing.toBuilder()
            .enName(updatedEnName)
            .frName(updatedFrName)
            .enDescription(updatedEnDescription)
            .frDescription(updatedFrDescription)
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
    return Optional.of(loadAccessibleTemplate(id)).map(this::toResponse);
  }

  @Override
  public DocumentTemplateDownload download(Long id) {
    DocumentTemplateEntity entity = loadAccessibleTemplate(id);

    try {
      return DocumentTemplateDownload.builder()
          .name(entity.getEnName())
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
    if (request == null || CollectionUtils.isEmpty(request.getDocuments())) {
      throw ValidationErrorException.of(
          DocumentTemplateGenerateErrorCode.GENERATE_DOCUMENTS_REQUIRED);
    }

    List<TemplateRenderSource> renderSources = new ArrayList<>();
    for (DocumentTemplateGenerateRequest.GenerateInput input : request.getDocuments()) {
      if (input == null || input.getDocumentTemplateId() == null) {
        throw ValidationErrorException.of(
            DocumentTemplateGenerateErrorCode.GENERATE_TEMPLATE_ID_REQUIRED);
      }
      DocumentTemplateEntity entity = loadAccessibleTemplate(input.getDocumentTemplateId());

      byte[] sourceBytes = readStoredBytes(entity.getStoragePath());
      renderSources.add(
          new TemplateRenderSource(entity.getMimeType(), sourceBytes, input.getFields()));
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
  public PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("f.*")
            .from("document_templates", "f")
            .whereOr(
                WhereClause.create("upper(f.en_name)", Operator.LIKE, criteria.getNamePattern()),
                WhereClause.create("upper(f.fr_name)", Operator.LIKE, criteria.getNamePattern()));
    Integer userTenantId = currentTenantId();
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
    DocumentTemplateEntity entity = loadAccessibleTemplate(id);

    try {
      storageApi.delete(entity.getStoragePath());
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete stored file", e);
    }

    repository.delete(entity);
    auditLogApi.recordDelete(DOCUMENT_TEMPLATES_RESOURCE_TYPE, id, entity);
  }

  private void validateUploadRequest(DocumentTemplateUploadCommand command) {
    if (command == null) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.FILE_REQUIRED);
    }
    validateUploadTenantAccess(command.getTenantId());
    validateFilePart(
        command.getContentSize(), command.getContentStream(), command.getOriginalFilename());
  }

  private void validateReplacementRequest(DocumentTemplateUpdateCommand command) {
    validateFilePart(
        command.getContentSize(), command.getContentStream(), command.getOriginalFilename());
  }

  private void validateFilePart(
      Long contentSize, InputStream contentStream, String originalFilename) {
    if (contentSize == null || contentSize <= 0 || contentStream == null) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.FILE_REQUIRED);
    }
    if (originalFilename == null || originalFilename.isBlank()) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.ORIGINAL_FILENAME_REQUIRED);
    }
    if (contentSize > maxUploadSizeBytes) {
      throw new PayloadTooLargeException(
          "Uploaded file exceeds max allowed size of " + maxUploadSizeBytes + " bytes");
    }
  }

  private static String normalizeMimeType(String mimeType, String originalFilename) {
    String fileName = originalFilename.toLowerCase(Locale.ROOT);
    if (StringUtils.isNotBlank(mimeType)) {
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
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.UNSUPPORTED_FILE_TYPE);
    }
    if (fileName.endsWith(".pdf")) {
      return MIME_PDF;
    }
    if (fileName.endsWith(".docx")) {
      return MIME_DOCX;
    }
    throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.UNSUPPORTED_FILE_TYPE);
  }

  private static String coalesceTrimmedRequired(String nextValue, String existingValue) {
    String trimmedValue = StringUtils.trimToNull(nextValue);
    return trimmedValue == null ? existingValue : trimmedValue;
  }

  private static String coalesceTrimmedNullable(String nextValue, String existingValue) {
    if (nextValue == null) {
      return existingValue;
    }
    return StringUtils.trimToNull(nextValue);
  }

  private boolean hasUnchangedFieldMap(String previousFormMapJson, String nextFormMapJson) {
    JsonNode previous = parseJsonOrNull(previousFormMapJson);
    JsonNode next = parseJsonOrNull(nextFormMapJson);
    return Objects.equals(previous, next);
  }

  private JsonNode parseJsonOrNull(String rawJson) {
    if (StringUtils.isBlank(rawJson)) {
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
        .enName(entity.getEnName())
        .frName(entity.getFrName())
        .enDescription(entity.getEnDescription())
        .frDescription(entity.getFrDescription())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .checksumSha256(entity.getChecksumSha256())
        .tenantId(entity.getTenantId())
        .formMap(parseJsonOrNull(entity.getFormMapJson()))
        .esignable(entity.isEsignable())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }

  private void validateUploadTenantAccess(Integer requestTenantId) {
    if (!hasTenantScopeAccess(requestTenantId, false)) {
      throw ValidationErrorException.of(DocumentTemplateUploadErrorCode.TENANT_SCOPE_MISMATCH);
    }
  }

  private boolean hasTenantScopeAccess(Integer tenantId, boolean allowGlobalResource) {
    Integer userTenantId = currentTenantId();
    if (userTenantId == null) {
      return true;
    }
    if (tenantId == null) {
      return allowGlobalResource;
    }
    return userTenantId.equals(tenantId);
  }

  private Integer currentTenantId() {
    return currentUserProvider.currentTenantId().orElse(null);
  }

  private DocumentTemplateEntity loadAccessibleTemplate(Long templateId) {
    DocumentTemplateEntity entity =
        repository
            .findById(templateId)
            .orElseThrow(
                () -> new NotFoundException("Document template not found with id: " + templateId));
    if (!hasTenantScopeAccess(entity.getTenantId(), true)) {
      throw new NotFoundException("Document template not found with id: " + templateId);
    }
    return entity;
  }

  private byte[] readRequestPayload(InputStream contentStream) {
    try {
      return contentStream.readAllBytes();
    } catch (IOException e) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.FILE_READ_FAILED);
    }
  }

  private StorageWriteResult persistPayload(String originalFilename, byte[] payload) {
    try {
      return storageApi.write(
          StorageWriteRequest.builder()
              .namespace(STORAGE_NAMESPACE_DOCUMENT_TEMPLATES)
              .originalFilename(originalFilename)
              .contentStream(new ByteArrayInputStream(payload))
              .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist uploaded file", e);
    }
  }

  private byte[] readStoredBytes(String storagePath) {
    try (InputStream inputStream = storageApi.read(storagePath)) {
      return inputStream.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored file", e);
    }
  }

  private static boolean hasReplacementContent(DocumentTemplateUpdateCommand command) {
    return command != null && command.getContentStream() != null;
  }
}
