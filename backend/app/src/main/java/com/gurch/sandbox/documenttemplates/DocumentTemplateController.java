package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.web.NotFoundException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/admin/document-templates")
@RequiredArgsConstructor
public class DocumentTemplateController {

  private final DocumentTemplateApi documentTemplateApi;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  // Multipart uploads are excluded from idempotency replay because the request body is streamed.
  @NotIdempotent
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "templateKey", required = false) String templateKey,
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "description", required = false) String description,
      @RequestParam(value = "tenantId", required = false) Integer tenantId) {
    DocumentTemplateUploadRequest request;
    try {
      request =
          new DocumentTemplateUploadRequest(
              templateKey,
              name,
              description,
              tenantId,
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              file.getInputStream());
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not read uploaded file content");
    }

    return new CreateResponse(documentTemplateApi.upload(request).getId());
  }

  @GetMapping("/{id}")
  public DocumentTemplateResponse getById(@PathVariable Long id) {
    return documentTemplateApi
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<StreamingResponseBody> download(@PathVariable Long id) {
    DocumentTemplateDownload download = documentTemplateApi.download(id);
    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    try {
      mediaType = MediaType.parseMediaType(download.getMimeType());
    } catch (Exception ignored) {
      // Fall back to octet-stream when media type is invalid or not parseable.
    }

    StreamingResponseBody body =
        outputStream -> {
          try (InputStream inputStream = download.getContentStream()) {
            inputStream.transferTo(outputStream);
          }
        };

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(download.getName()).build().toString())
        .contentLength(download.getContentSize())
        .body(body);
  }

  @GetMapping("/search")
  public PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria) {
    return documentTemplateApi.search(criteria);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    documentTemplateApi.deleteById(id);
  }
}
