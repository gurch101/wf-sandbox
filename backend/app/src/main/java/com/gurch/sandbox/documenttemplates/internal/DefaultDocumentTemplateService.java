package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.audit.AuditLogApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateApi;
import com.gurch.sandbox.documenttemplates.DocumentTemplateDownload;
import com.gurch.sandbox.documenttemplates.DocumentTemplateGenerateRequest;
import com.gurch.sandbox.documenttemplates.DocumentTemplateResponse;
import com.gurch.sandbox.documenttemplates.DocumentTemplateSearchCriteria;
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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
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
  private static final Pattern HANDLEBARS_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
  private static final Pattern EL_PLACEHOLDER = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_.-]+)\\s*}");
  private static final String STORAGE_NAMESPACE_DOCUMENT_TEMPLATES = "document-templates";
  private static final String DOCUMENT_TEMPLATES_RESOURCE_TYPE = "documenttemplates";

  private final DocumentTemplateRepository repository;
  private final StorageApi storageApi;
  private final SearchExecutor searchExecutor;
  private final AuditLogApi auditLogApi;
  private final DocumentTemplateIntrospectionService introspectionService;
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
  public Optional<DocumentTemplateResponse> findById(Long id) {
    return repository.findById(id).filter(this::isAccessibleToCurrentTenant).map(this::toResponse);
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

    List<byte[]> renderedPdfs = new ArrayList<>();
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

      Map<String, String> fieldValues = toFieldValues(input.getFields());
      byte[] sourceBytes = readStoredBytes(entity.getStoragePath());
      renderedPdfs.add(renderTemplateToPdf(entity.getMimeType(), sourceBytes, fieldValues));
    }

    byte[] mergedPdf = mergePdfs(renderedPdfs);
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
      if (MIME_PDF.equals(normalized)
          || MIME_DOC.equals(normalized)
          || MIME_DOCX.equals(normalized)) {
        return normalized;
      }
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

  private byte[] renderTemplateToPdf(
      String mimeType, byte[] sourceBytes, Map<String, String> fields) {
    return switch (mimeType) {
      case MIME_PDF -> fillAndFlattenPdf(sourceBytes, fields);
      case MIME_DOC, MIME_DOCX -> renderWordAsPdf(mimeType, sourceBytes, fields);
      default ->
          throw new IllegalArgumentException(
              "Unsupported file type. Only PDF and Word documents are allowed");
    };
  }

  private byte[] fillAndFlattenPdf(byte[] sourceBytes, Map<String, String> fields) {
    try (PDDocument document = Loader.loadPDF(sourceBytes);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          String fieldName = firstNonBlank(field.getFullyQualifiedName(), field.getPartialName());
          if (fieldName == null) {
            continue;
          }
          String value = fields.get(fieldName);
          if (value == null) {
            continue;
          }
          applyPdfFieldValue(field, value);
        }
        acroForm.flatten();
      }
      document.save(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to render PDF template", e);
    }
  }

  private void applyPdfFieldValue(PDField field, String value) throws IOException {
    if (field instanceof PDCheckBox checkBox) {
      if (isTruthy(value) || value.equalsIgnoreCase(checkBox.getOnValue())) {
        checkBox.check();
      } else {
        checkBox.unCheck();
      }
      return;
    }
    field.setValue(value);
  }

  private byte[] renderWordAsPdf(String mimeType, byte[] sourceBytes, Map<String, String> fields) {
    String rawText = extractWordText(mimeType, sourceBytes);
    String mergedText = mergeWordText(rawText, fields);
    return textToPdf(mergedText);
  }

  private String extractWordText(String mimeType, byte[] sourceBytes) {
    try {
      if (MIME_DOC.equals(mimeType)) {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(sourceBytes));
            WordExtractor extractor = new WordExtractor(document)) {
          return extractor.getText();
        }
      }
      try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
          XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
        return extractor.getText();
      }
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to render Word template", e);
    }
  }

  private static String mergeWordText(String text, Map<String, String> fields) {
    String merged = replacePlaceholders(text == null ? "" : text, HANDLEBARS_PLACEHOLDER, fields);
    return replacePlaceholders(merged, EL_PLACEHOLDER, fields);
  }

  private static String replacePlaceholders(
      String text, Pattern pattern, Map<String, String> values) {
    Matcher matcher = pattern.matcher(text);
    StringBuffer output = new StringBuffer();
    while (matcher.find()) {
      String replacement = values.getOrDefault(matcher.group(1).trim(), "");
      matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
    }
    matcher.appendTail(output);
    return output.toString();
  }

  private byte[] textToPdf(String text) {
    try (PDDocument document = new PDDocument();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
      float fontSize = 11f;
      float leading = 14f;
      float margin = 50f;
      int maxCharsPerLine = 100;
      List<String> lines = wrapText(text == null ? "" : text, maxCharsPerLine);
      if (lines.isEmpty()) {
        lines = List.of("");
      }

      PDPage page = null;
      PDPageContentStream contentStream = null;
      float y = 0f;
      for (String line : lines) {
        if (page == null || y - leading < margin) {
          if (contentStream != null) {
            contentStream.endText();
            contentStream.close();
          }
          page = new PDPage(PDRectangle.LETTER);
          document.addPage(page);
          contentStream = new PDPageContentStream(document, page);
          contentStream.beginText();
          contentStream.setFont(font, fontSize);
          y = page.getMediaBox().getHeight() - margin;
          contentStream.newLineAtOffset(margin, y);
        }
        contentStream.showText(sanitizePdfText(line));
        y -= leading;
        contentStream.newLineAtOffset(0, -leading);
      }
      if (contentStream != null) {
        contentStream.endText();
        contentStream.close();
      }

      document.save(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to compose PDF output", e);
    }
  }

  private static List<String> wrapText(String text, int maxCharsPerLine) {
    List<String> lines = new ArrayList<>();
    for (String rawLine : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
      if (rawLine.length() <= maxCharsPerLine) {
        lines.add(rawLine);
        continue;
      }
      String remaining = rawLine;
      while (remaining.length() > maxCharsPerLine) {
        int breakAt = remaining.lastIndexOf(' ', maxCharsPerLine);
        if (breakAt <= 0) {
          breakAt = maxCharsPerLine;
        }
        lines.add(remaining.substring(0, breakAt).stripTrailing());
        remaining = remaining.substring(breakAt).stripLeading();
      }
      lines.add(remaining);
    }
    return lines;
  }

  private static String sanitizePdfText(String text) {
    return text.replace('\t', ' ').replace('\u0000', ' ');
  }

  private static byte[] mergePdfs(List<byte[]> renderedPdfs) {
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PDFMergerUtility merger = new PDFMergerUtility();
      List<RandomAccessReadBuffer> sources = new ArrayList<>();
      try {
        for (byte[] bytes : renderedPdfs) {
          RandomAccessReadBuffer source = new RandomAccessReadBuffer(bytes);
          sources.add(source);
          merger.addSource(source);
        }
        merger.setDestinationStream(outputStream);
        merger.mergeDocuments(IOUtils.createMemoryOnlyStreamCache());
      } finally {
        for (RandomAccessReadBuffer source : sources) {
          source.close();
        }
      }
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Unable to compose merged PDF output", e);
    }
  }

  private static Map<String, String> toFieldValues(Map<String, Object> fieldsNode) {
    if (fieldsNode == null || fieldsNode.isEmpty()) {
      return Map.of();
    }
    Map<String, String> fields = new java.util.LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fieldsNode.entrySet()) {
      Object value = entry.getValue();
      if (value == null) {
        fields.put(entry.getKey(), "");
      } else {
        fields.put(entry.getKey(), String.valueOf(value));
      }
    }
    return fields;
  }

  private static String firstNonBlank(String first, String second) {
    if (first != null && !first.isBlank()) {
      return first;
    }
    if (second != null && !second.isBlank()) {
      return second;
    }
    return null;
  }

  private static boolean isTruthy(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    return normalized.equals("true")
        || normalized.equals("1")
        || normalized.equals("yes")
        || normalized.equals("on");
  }
}
