package com.gurch.sandbox.esign;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gurch.sandbox.esign.internal.DocuSignConnectWebhookMapper;
import com.gurch.sandbox.esign.internal.DocuSignConnectWebhookPayload;
import com.gurch.sandbox.esign.internal.DocuSignWebhookVerifier;
import com.gurch.sandbox.idempotency.NotIdempotent;
import com.gurch.sandbox.security.SystemAuthenticationScope;
import com.gurch.sandbox.web.ApiErrorEnum;
import com.gurch.sandbox.web.NotFoundException;
import com.gurch.sandbox.web.ValidationErrorException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/esign")
@RequiredArgsConstructor
@Tag(
    name = "E-Sign",
    description = "Create, track, reconcile, and download DocuSign-backed e-sign workflows")
public class EsignController {

  private final EsignApi esignApi;
  private final EsignReconciliationApi esignReconciliationApi;
  private final EsignWebhookApi esignWebhookApi;
  private final DocuSignWebhookVerifier webhookVerifier;
  private final DocuSignConnectWebhookMapper webhookMapper;
  private final ObjectMapper objectMapper;
  private final SystemAuthenticationScope systemAuthenticationScope;

  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @NotIdempotent
  @ApiErrorEnum({EsignCreateErrorCode.class})
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "Create an e-sign envelope from an uploaded PDF",
      operationId = "createEsignEnvelope")
  public EsignEnvelopeResponse create(
      @RequestPart("file") MultipartFile file,
      @Valid @RequestPart("request") EsignCreateEnvelopeRequest request) {
    try {
      return esignApi.createEnvelope(
          EsignCreateEnvelopeCommand.builder()
              .subject(request.subject())
              .message(request.message())
              .deliveryMode(request.deliveryMode())
              .remindersEnabled(request.remindersEnabled())
              .reminderIntervalHours(request.reminderIntervalHours())
              .originalFilename(file.getOriginalFilename())
              .contentSize(file.getSize())
              .contentStream(file.getInputStream())
              .signers(request.signers())
              .build());
    } catch (IOException e) {
      throw ValidationErrorException.of(EsignCreateErrorCode.FILE_READ_FAILED);
    }
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an e-sign envelope by id", operationId = "getEsignEnvelopeById")
  public EsignEnvelopeResponse getById(@PathVariable Long id) {
    return esignApi
        .findById(id)
        .orElseThrow(() -> new NotFoundException("E-sign envelope not found with id: " + id));
  }

  @PostMapping("/{id}/void")
  @ApiErrorEnum({EsignVoidErrorCode.class})
  @Operation(summary = "Void an active e-sign envelope", operationId = "voidEsignEnvelope")
  public EsignEnvelopeResponse voidEnvelope(
      @PathVariable Long id, @Valid @RequestBody EsignVoidRequest request) {
    return esignApi.voidEnvelope(id, request.reason());
  }

  @PostMapping("/{id}/resend")
  @ApiErrorEnum({EsignResendErrorCode.class})
  @Operation(
      summary = "Resend notification emails for actionable remote signers",
      operationId = "resendEsignEnvelope")
  public EsignEnvelopeResponse resendEnvelope(@PathVariable Long id) {
    return esignApi.resendEnvelope(id);
  }

  @PostMapping("/{id}/signers/{roleKey}/resend")
  @ApiErrorEnum({EsignResendErrorCode.class})
  @Operation(
      summary = "Resend the notification email for one actionable remote signer",
      operationId = "resendEsignSigner")
  public EsignEnvelopeResponse resendSigner(@PathVariable Long id, @PathVariable String roleKey) {
    return esignApi.resendSigner(id, roleKey);
  }

  @PostMapping("/reconcile")
  @Operation(
      summary = "Reconcile active e-sign envelopes against DocuSign",
      operationId = "reconcileEsignEnvelopes")
  public EsignReconcileResponse reconcileActiveEnvelopes() {
    return esignReconciliationApi.reconcileActiveEnvelopes();
  }

  @PostMapping("/{id}/signers/{roleKey}/embedded-view")
  @ApiErrorEnum({EsignEmbeddedViewErrorCode.class})
  @Operation(
      summary = "Create an embedded signing view for one in-person signer",
      operationId = "createEsignEmbeddedView")
  public EsignEmbeddedViewResponse createEmbeddedView(
      @PathVariable Long id,
      @PathVariable String roleKey,
      @RequestParam(required = false) String locale) {
    return esignApi.createEmbeddedSigningView(id, roleKey, locale);
  }

  @GetMapping("/{id}/certificate")
  @ApiErrorEnum({EsignDownloadErrorCode.class})
  @Operation(
      summary = "Download a stored signing certificate",
      operationId = "downloadEsignCertificate")
  public ResponseEntity<InputStreamResource> downloadCertificate(@PathVariable Long id) {
    EsignDocumentDownload download = esignApi.downloadCertificate(id);
    return toDownloadResponse(
        download.getMimeType(),
        download.getContentSize(),
        download.getFileName(),
        download.getContentStream());
  }

  @GetMapping("/{id}/signed-document")
  @ApiErrorEnum({EsignDownloadErrorCode.class})
  @Operation(
      summary = "Download a stored signed document",
      operationId = "downloadEsignSignedDocument")
  public ResponseEntity<InputStreamResource> downloadSignedDocument(@PathVariable Long id) {
    EsignDocumentDownload download = esignApi.downloadSignedDocument(id);
    return toDownloadResponse(
        download.getMimeType(),
        download.getContentSize(),
        download.getFileName(),
        download.getContentStream());
  }

  private ResponseEntity<InputStreamResource> toDownloadResponse(
      String mimeType, long contentSize, String fileName, InputStream contentStream) {
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
  @Operation(
      summary = "Receive DocuSign Connect webhook events",
      operationId = "receiveDocuSignWebhook")
  public void webhook(
      @RequestHeader(value = DocuSignWebhookVerifier.SIGNATURE_HEADER_NAME, required = false)
          String signature,
      @RequestBody byte[] rawPayload) {
    webhookVerifier.verify(signature, rawPayload);
    try {
      DocuSignConnectWebhookPayload payload =
          objectMapper.readValue(rawPayload, DocuSignConnectWebhookPayload.class);
      EsignWebhookRequest request = webhookMapper.map(payload);
      if (request == null) {
        return;
      }
      systemAuthenticationScope.run(() -> esignWebhookApi.handleWebhook(request));
    } catch (IOException e) {
      throw new IllegalArgumentException("Invalid DocuSign webhook payload", e);
    }
  }
}
