package com.gurch.sandbox.documenttemplates;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.dto.PagedResponse;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/document-templates")
@RequiredArgsConstructor
@Tag(
    name = "Document Templates",
    description = "Manage uploaded document templates and generated documents")
public class DocumentTemplateController {

  private final DocumentTemplateApi documentTemplateApi;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  // Multipart uploads are excluded from idempotency replay because the request body is streamed.
  @NotIdempotent
  @ApiErrorEnum({DocumentTemplateSharedErrorCode.class, DocumentTemplateUploadErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(summary = "Upload a document template")
  public CreateResponse upload(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("request") DocumentTemplateUploadRequest request) {
    DocumentTemplateUploadCommand command;
    try {
      command =
          new DocumentTemplateUploadCommand(
              request.enName(),
              request.frName(),
              request.enDescription(),
              request.frDescription(),
              request.language(),
              request.tenantId(),
              file.getOriginalFilename(),
              file.getContentType(),
              file.getSize(),
              file.getInputStream());
    } catch (IOException e) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.FILE_READ_FAILED);
    }

    return new CreateResponse(documentTemplateApi.upload(command).getId());
  }

  @PatchMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @NotIdempotent
  @ApiErrorEnum({DocumentTemplateSharedErrorCode.class, DocumentTemplateUpdateErrorCode.class})
  @Operation(summary = "Patch a document template")
  public DocumentTemplateResponse update(
      @PathVariable Long id,
      @RequestPart(value = "file", required = false) MultipartFile file,
      @Valid @RequestPart("request") DocumentTemplateUpdateRequest request) {
    DocumentTemplateUpdateCommand command;
    try {
      command =
          new DocumentTemplateUpdateCommand(
              request.enName(),
              request.frName(),
              request.enDescription(),
              request.frDescription(),
              file == null ? null : file.getOriginalFilename(),
              file == null ? null : file.getContentType(),
              file == null ? null : file.getSize(),
              file == null ? null : file.getInputStream());
    } catch (IOException e) {
      throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.FILE_READ_FAILED);
    }
    return documentTemplateApi.update(id, command);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a document template by id")
  public DocumentTemplateResponse getById(@PathVariable Long id) {
    return documentTemplateApi
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Document template not found with id: " + id));
  }

  @GetMapping("/{id}/download")
  @Operation(summary = "Download a document template file")
  public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
    DocumentTemplateDownload download = documentTemplateApi.download(id);
    return toDownloadResponse(download);
  }

  @PostMapping("/generate")
  @NotIdempotent
  @ApiErrorEnum({DocumentTemplateSharedErrorCode.class, DocumentTemplateGenerateErrorCode.class})
  @Operation(summary = "Generate a merged PDF from document templates")
  public ResponseEntity<InputStreamResource> generate(
      @Valid @RequestBody DocumentTemplateGenerateRequest request) {
    DocumentTemplateDownload download = documentTemplateApi.generate(request);
    return toDownloadResponse(download);
  }

  private ResponseEntity<InputStreamResource> toDownloadResponse(
      DocumentTemplateDownload download) {
    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    try {
      mediaType = MediaType.parseMediaType(download.getMimeType());
    } catch (Exception ignored) {
      // Fall back to octet-stream when media type is invalid or not parseable.
    }

    return ResponseEntity.ok()
        .contentType(mediaType)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(download.getName()).build().toString())
        .contentLength(download.getContentSize())
        .body(new InputStreamResource(download.getContentStream()));
  }

  @GetMapping("/search")
  @Operation(summary = "Search document templates")
  public PagedResponse<DocumentTemplateResponse> search(
      @ParameterObject DocumentTemplateSearchCriteria criteria) {
    return documentTemplateApi.search(criteria);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a document template")
  public void delete(@PathVariable Long id) {
    documentTemplateApi.deleteById(id);
  }
}
