package com.gurch.sandbox.esign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.esign.internal.DocuSignWebhookVerifier;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
import jakarta.validation.Valid;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/esign/envelopes")
@RequiredArgsConstructor
public class EsignController {

  private final EsignApi esignApi;
  private final DocuSignWebhookVerifier webhookVerifier;
  private final ObjectMapper objectMapper;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @NotIdempotent
  @ApiErrorEnum({EsignErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  public EsignEnvelopeResponse create(
      @RequestPart("file") MultipartFile file, @Valid @RequestPart("request") EsignCreateEnvelopeRequest request) {
    try {
      return esignApi.createEnvelope(
          EsignCreateEnvelopeCommand.builder()
              .subject(request.subject())
              .message(request.message())
              .deliveryMode(request.deliveryMode())
              .remindersEnabled(request.remindersEnabled())
              .reminderIntervalHours(request.reminderIntervalHours())
              .originalFilename(file.getOriginalFilename())
              .mimeType(file.getContentType())
              .contentSize(file.getSize())
              .contentStream(file.getInputStream())
              .signers(request.signers())
              .build());
    } catch (IOException e) {
      throw com.gurch.sandbox.web.ValidationErrorException.of(EsignErrorCode.FILE_READ_FAILED);
    }
  }

  @GetMapping("/{id}")
  public EsignEnvelopeResponse getById(@PathVariable Long id) {
    return esignApi
        .findById(id)
        .orElseThrow(() -> new NotFoundException("E-sign envelope not found with id: " + id));
  }

  @PostMapping("/{id}/void")
  @ApiErrorEnum({EsignErrorCode.class})
  public EsignEnvelopeResponse voidEnvelope(
      @PathVariable Long id, @Valid @RequestBody EsignVoidRequest request) {
    return esignApi.voidEnvelope(id, request.reason());
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable Long id) {
    esignApi.deleteEnvelope(id);
  }

  @PostMapping("/{id}/signers/{roleKey}/embedded-view")
  @ApiErrorEnum({EsignErrorCode.class})
  public EsignEmbeddedViewResponse createEmbeddedView(
      @PathVariable Long id, @PathVariable String roleKey) {
    return esignApi.createEmbeddedSigningView(id, roleKey);
  }

  @GetMapping("/{id}/certificate")
  @ApiErrorEnum({EsignErrorCode.class})
  public ResponseEntity<InputStreamResource> downloadCertificate(@PathVariable Long id) {
    EsignCertificateDownload download = esignApi.downloadCertificate(id);
    return toDownloadResponse(download.getMimeType(), download.getContentSize(), download.getFileName(), download.getContentStream());
  }

  @GetMapping("/{id}/signed-document")
  @ApiErrorEnum({EsignErrorCode.class})
  public ResponseEntity<InputStreamResource> downloadSignedDocument(@PathVariable Long id) {
    EsignSignedDocumentDownload download = esignApi.downloadSignedDocument(id);
    return toDownloadResponse(download.getMimeType(), download.getContentSize(), download.getFileName(), download.getContentStream());
  }

  private ResponseEntity<InputStreamResource> toDownloadResponse(
      String mimeType, long contentSize, String fileName, java.io.InputStream contentStream) {
    MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
    try {
      mediaType = MediaType.parseMediaType(mimeType);
    } catch (Exception ignored) {
      // Fall back to octet-stream when the stored mime type is not parseable.
    }
    return ResponseEntity.ok()
        .contentType(mediaType)
        .contentLength(contentSize)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename(fileName).build().toString())
        .body(new InputStreamResource(contentStream));
  }

  @PostMapping("/webhooks/docusign")
  @NotIdempotent
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void webhook(
      @org.springframework.web.bind.annotation.RequestHeader(
              value = DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME,
              required = false)
          String signature,
      @RequestBody byte[] rawPayload) {
    webhookVerifier.verify(signature, rawPayload);
    try {
      esignApi.handleWebhook(objectMapper.readValue(rawPayload, EsignWebhookRequest.class));
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid DocuSign webhook payload", e);
    }
  }
}
