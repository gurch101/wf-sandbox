package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.forms.DocumentTemplateApi;
import com.gurch.sandbox.forms.DocumentTemplateDownload;
import com.gurch.sandbox.forms.DocumentTemplateResponse;
import com.gurch.sandbox.forms.DocumentTemplateSearchCriteria;
import com.gurch.sandbox.forms.DocumentTemplateType;
import com.gurch.sandbox.forms.DocumentTemplateUploadRequest;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.query.SearchExecutor;
import com.gurch.sandbox.storage.StorageApi;
import com.gurch.sandbox.storage.StorageWriteRequest;
import com.gurch.sandbox.storage.StorageWriteResult;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.PayloadTooLargeException;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DefaultDocumentTemplateService implements DocumentTemplateApi {

  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOC = "application/msword";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final String STORAGE_NAMESPACE_DOCUMENT_TEMPLATES = "document-templates";

  private final DocumentTemplateRepository repository;
  private final StorageApi storageApi;
  private final SearchExecutor searchExecutor;

  @Value("${documenttemplates.upload.max-size-bytes:26214400}")
  private long maxUploadSizeBytes;

  @Override
  @Transactional
  public DocumentTemplateResponse upload(DocumentTemplateUploadRequest request) {
    validateUploadRequest(request);
    validateUploadSize(request.getContentSize());

    String mimeType = normalizeMimeType(request.getMimeType(), request.getOriginalFilename());
    DocumentTemplateType documentType =
        resolveDocumentType(mimeType, request.getOriginalFilename());
    String displayName = resolveDisplayName(request.getName(), request.getOriginalFilename());

    StorageWriteResult stored;
    try {
      stored =
          storageApi.write(
              StorageWriteRequest.builder()
                  .namespace(STORAGE_NAMESPACE_DOCUMENT_TEMPLATES)
                  .originalFilename(request.getOriginalFilename())
                  .contentStream(request.getContentStream())
                  .build());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist uploaded file", e);
    }

    DocumentTemplateEntity entity =
        DocumentTemplateEntity.builder()
            .name(displayName)
            .description(trimToNull(request.getDescription()))
            .mimeType(mimeType)
            .contentSize(stored.contentSize())
            .checksumSha256(stored.checksumSha256())
            .documentType(documentType)
            .storageProvider(stored.provider())
            .storagePath(stored.storagePath())
            .build();

    try {
      return toResponse(repository.save(entity));
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
    return repository.findById(id).map(this::toResponse);
  }

  @Override
  public DocumentTemplateDownload download(Long id) {
    DocumentTemplateEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));

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
  public PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("f.*")
            .from("forms", "f")
            .where("upper(f.name)", Operator.LIKE, criteria.getNamePattern())
            .where("f.document_type", Operator.IN, criteria.getDocumentTypes());

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

    try {
      storageApi.delete(entity.getStoragePath());
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete stored file", e);
    }

    repository.delete(entity);
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

  private void validateUploadSize(Long contentSize) {
    if (contentSize > maxUploadSizeBytes) {
      throw new PayloadTooLargeException(
          "Uploaded file exceeds max allowed size of " + maxUploadSizeBytes + " bytes");
    }
  }

  private static String normalizeMimeType(String mimeType, String originalFilename) {
    if (mimeType != null && !mimeType.isBlank()) {
      return mimeType.trim().toLowerCase(Locale.ROOT);
    }

    String fileName = originalFilename.toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".pdf")) {
      return MIME_PDF;
    }
    if (fileName.endsWith(".doc")) {
      return MIME_DOC;
    }
    if (fileName.endsWith(".docx")) {
      return MIME_DOCX;
    }
    throw new IllegalArgumentException(
        "Unsupported file type. Only PDF and Word documents are allowed");
  }

  private static DocumentTemplateType resolveDocumentType(
      String mimeType, String originalFilename) {
    if (MIME_PDF.equals(mimeType)) {
      return DocumentTemplateType.PDF_FORM;
    }
    if (MIME_DOC.equals(mimeType) || MIME_DOCX.equals(mimeType)) {
      return DocumentTemplateType.WORD_DOCUMENT;
    }

    String fileName = originalFilename.toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".pdf")) {
      return DocumentTemplateType.PDF_FORM;
    }
    if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
      return DocumentTemplateType.WORD_DOCUMENT;
    }

    throw new IllegalArgumentException(
        "Unsupported file type. Only PDF and Word documents are allowed");
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

  private DocumentTemplateResponse toResponse(DocumentTemplateEntity entity) {
    return DocumentTemplateResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .checksumSha256(entity.getChecksumSha256())
        .documentType(entity.getDocumentType())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }
}
