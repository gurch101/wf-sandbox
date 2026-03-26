package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.DocumentTemplateSharedErrorCode;
import com.gurch.sandbox.web.ValidationErrorException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.springframework.stereotype.Service;

/** Orchestrates per-template rendering and composes all rendered outputs into one PDF. */
@Service
@RequiredArgsConstructor
public class DocumentTemplateGenerationService {

  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final DocumentTemplatePdfGenerationService pdfGenerationService;
  private final DocumentTemplateDocxGenerationService docxGenerationService;

  public byte[] generateComposedPdf(List<TemplateRenderSource> sources) {
    List<byte[]> renderedPdfs = new ArrayList<>();
    for (TemplateRenderSource source : sources) {
      renderedPdfs.add(
          renderTemplateToPdf(source.getMimeType(), source.getContent(), source.getFields()));
    }
    return mergePdfs(renderedPdfs);
  }

  private byte[] renderTemplateToPdf(
      String mimeType, byte[] sourceBytes, Map<String, Object> fields) {
    return switch (mimeType) {
      case MIME_PDF -> pdfGenerationService.renderAsPdf(sourceBytes, fields);
      case MIME_DOCX -> docxGenerationService.renderAsPdf(sourceBytes, fields);
      default ->
          throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.UNSUPPORTED_FILE_TYPE);
    };
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
}
