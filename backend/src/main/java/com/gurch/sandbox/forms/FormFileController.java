package com.gurch.sandbox.forms;

import com.gurch.sandbox.dto.CreateResponse;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.web.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
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

/** REST controller for form-file upload, metadata lookup, download, search, and deletion. */
@RestController
@RequestMapping("/api/forms/files")
@RequiredArgsConstructor
public class FormFileController {

  private final FormFileApi formFileApi;

  /** Uploads a PDF or Word form file. */
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @NotIdempotent
  @ResponseStatus(HttpStatus.CREATED)
  public CreateResponse upload(
      @RequestPart("file") MultipartFile file,
      @RequestParam(value = "name", required = false) String name,
      @RequestParam(value = "description", required = false) String description) {
    FormFileUploadRequest request;
    try {
      request =
          new FormFileUploadRequest(
              name,
              description,
              file.getOriginalFilename(),
              file.getContentType(),
              file.getBytes());
    } catch (Exception e) {
      throw new IllegalArgumentException("Could not read uploaded file content");
    }

    return new CreateResponse(formFileApi.upload(request).getId());
  }

  /** Gets file metadata by ID. */
  @GetMapping("/{id}")
  public FormFileResponse getById(@PathVariable Long id) {
    return formFileApi
        .findById(id)
        .orElseThrow(() -> new NotFoundException("Form file not found with id: " + id));
  }

  /** Downloads stored file bytes by ID. */
  @GetMapping("/{id}/download")
  public ResponseEntity<Resource> download(@PathVariable Long id) {
    FormFileDownload download = formFileApi.download(id);
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
        .body(new ByteArrayResource(download.getContent()));
  }

  /** Searches files using optional filters and pagination. */
  @GetMapping("/search")
  public FormFileDtos.SearchResponse search(FormFileSearchCriteria criteria) {
    return new FormFileDtos.SearchResponse(formFileApi.search(criteria));
  }

  /** Deletes file metadata and stored bytes by ID. */
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    formFileApi.deleteById(id);
  }
}
