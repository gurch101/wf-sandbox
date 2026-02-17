package com.gurch.sandbox.forms.internal;

import com.gurch.sandbox.forms.FormDocumentType;
import com.gurch.sandbox.forms.FormFileApi;
import com.gurch.sandbox.forms.FormFileDownload;
import com.gurch.sandbox.forms.FormFileResponse;
import com.gurch.sandbox.forms.FormFileSearchCriteria;
import com.gurch.sandbox.forms.FormFileUploadRequest;
import com.gurch.sandbox.forms.FormSignatureStatus;
import com.gurch.sandbox.query.BuiltQuery;
import com.gurch.sandbox.query.Operator;
import com.gurch.sandbox.query.SQLQueryBuilder;
import com.gurch.sandbox.web.NotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.security.MessageDigest;
import java.util.HexFormat;
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
public class DefaultFormFileService implements FormFileApi {

  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOC = "application/msword";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final FormFileRepository repository;
  private final FormStorageProvider storageProvider;
  private final NamedParameterJdbcTemplate jdbcTemplate;

  @Override
  @Transactional
  public FormFileResponse upload(FormFileUploadRequest request) {
    validateUploadRequest(request);

    String mimeType = normalizeMimeType(request.getMimeType(), request.getOriginalFilename());
    FormDocumentType documentType = resolveDocumentType(mimeType, request.getOriginalFilename());
    String displayName = resolveDisplayName(request.getName(), request.getOriginalFilename());

    FormStorageWriteResult stored;
    try {
      stored = storageProvider.write(request.getOriginalFilename(), request.getContent());
    } catch (IOException e) {
      throw new IllegalStateException("Could not persist uploaded file", e);
    }

    FormFileEntity entity =
        FormFileEntity.builder()
            .name(displayName)
            .description(trimToNull(request.getDescription()))
            .mimeType(mimeType)
            .contentSize((long) request.getContent().length)
            .checksumSha256(sha256Hex(request.getContent()))
            .documentType(documentType)
            .storageProvider(stored.provider())
            .storagePath(stored.storagePath())
            .signatureStatus(FormSignatureStatus.NOT_REQUESTED)
            .build();

    try {
      return toResponse(repository.save(entity));
    } catch (RuntimeException e) {
      try {
        storageProvider.delete(stored.storagePath());
      } catch (IOException ignored) {
        // Intentionally ignored: persistence failure is the root cause.
      }
      throw e;
    }
  }

  @Override
  public Optional<FormFileResponse> findById(Long id) {
    return repository.findById(id).map(this::toResponse);
  }

  @Override
  public FormFileDownload download(Long id) {
    FormFileEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Form file not found with id: " + id));

    byte[] content;
    try {
      content = storageProvider.read(entity.getStoragePath());
    } catch (NoSuchFileException e) {
      throw new NotFoundException("Stored content is missing for form file id: " + id);
    } catch (IOException e) {
      throw new IllegalStateException("Could not read stored file", e);
    }

    return FormFileDownload.builder()
        .name(entity.getName())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .content(content)
        .build();
  }

  @Override
  public List<FormFileResponse> search(FormFileSearchCriteria criteria) {
    SQLQueryBuilder builder =
        SQLQueryBuilder.select("f.*")
            .from("forms_files", "f")
            .where("upper(f.name)", Operator.LIKE, criteria.getNamePattern())
            .where("upper(f.mime_type)", Operator.LIKE, criteria.getMimeTypePattern())
            .where("f.document_type", Operator.IN, criteria.getDocumentTypes())
            .where("f.signature_status", Operator.IN, criteria.getSignatureStatuses())
            .page(criteria.getPage(), criteria.getSize());

    BuiltQuery query = builder.build();
    return jdbcTemplate
        .query(query.sql(), query.params(), new DataClassRowMapper<>(FormFileEntity.class))
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public void deleteById(Long id) {
    FormFileEntity entity =
        repository
            .findById(id)
            .orElseThrow(() -> new NotFoundException("Form file not found with id: " + id));

    try {
      storageProvider.delete(entity.getStoragePath());
    } catch (IOException e) {
      throw new IllegalStateException("Could not delete stored file", e);
    }

    repository.delete(entity);
  }

  private static void validateUploadRequest(FormFileUploadRequest request) {
    if (request == null || request.getContent() == null || request.getContent().length == 0) {
      throw new IllegalArgumentException("file is required");
    }
    if (request.getOriginalFilename() == null || request.getOriginalFilename().isBlank()) {
      throw new IllegalArgumentException("original filename is required");
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

  private static FormDocumentType resolveDocumentType(String mimeType, String originalFilename) {
    if (MIME_PDF.equals(mimeType)) {
      return FormDocumentType.PDF_FORM;
    }
    if (MIME_DOC.equals(mimeType) || MIME_DOCX.equals(mimeType)) {
      return FormDocumentType.WORD_DOCUMENT;
    }

    String fileName = originalFilename.toLowerCase(Locale.ROOT);
    if (fileName.endsWith(".pdf")) {
      return FormDocumentType.PDF_FORM;
    }
    if (fileName.endsWith(".doc") || fileName.endsWith(".docx")) {
      return FormDocumentType.WORD_DOCUMENT;
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

  private static String sha256Hex(byte[] content) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
      return HexFormat.of().formatHex(digest);
    } catch (Exception e) {
      throw new IllegalStateException("Could not compute content checksum", e);
    }
  }

  private FormFileResponse toResponse(FormFileEntity entity) {
    return FormFileResponse.builder()
        .id(entity.getId())
        .name(entity.getName())
        .description(entity.getDescription())
        .mimeType(entity.getMimeType())
        .contentSize(entity.getContentSize())
        .checksumSha256(entity.getChecksumSha256())
        .documentType(entity.getDocumentType())
        .storageProvider(entity.getStorageProvider())
        .signatureStatus(entity.getSignatureStatus())
        .signatureEnvelopeId(entity.getSignatureEnvelopeId())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .version(entity.getVersion())
        .build();
  }
}
