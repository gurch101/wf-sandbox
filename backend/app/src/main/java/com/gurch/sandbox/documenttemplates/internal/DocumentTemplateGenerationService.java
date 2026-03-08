package com.gurch.sandbox.documenttemplates.internal;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
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
import org.springframework.stereotype.Service;

/** Renders PDF/Word templates and merges the rendered pages into one PDF document. */
@Service
@RequiredArgsConstructor
public class DocumentTemplateGenerationService {

  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOC = "application/msword";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  private static final Pattern HANDLEBARS_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
  private static final Pattern EL_PLACEHOLDER = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_.-]+)\\s*}");

  public byte[] generateComposedPdf(List<TemplateRenderSource> sources) {
    List<byte[]> renderedPdfs = new ArrayList<>();
    for (TemplateRenderSource source : sources) {
      Map<String, String> fieldValues = toFieldValues(source.getFields());
      renderedPdfs.add(renderTemplateToPdf(source.getMimeType(), source.getContent(), fieldValues));
    }
    return mergePdfs(renderedPdfs);
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
    Map<String, String> fields = new LinkedHashMap<>();
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

  @Getter
  public static final class TemplateRenderSource {
    private final String mimeType;
    private final byte[] content;
    private final Map<String, Object> fields;

    public TemplateRenderSource(String mimeType, byte[] content, Map<String, Object> fields) {
      this.mimeType = mimeType;
      this.content = content == null ? new byte[0] : content.clone();
      this.fields = fields == null ? Map.of() : Map.copyOf(fields);
    }

    public byte[] getContent() {
      return content.clone();
    }
  }
}
