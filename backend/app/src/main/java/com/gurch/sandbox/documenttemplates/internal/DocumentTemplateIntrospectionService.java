package com.gurch.sandbox.documenttemplates.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DocumentTemplateIntrospectionService {

  private static final Pattern HANDLEBARS_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
  private static final Pattern EL_PLACEHOLDER = Pattern.compile("\\$\\{\\s*([a-zA-Z0-9_.-]+)\\s*}");

  private static final List<String> PDF_ESIGN_ANCHORS = List.of("s1", "s2", "d1", "d2");
  private static final String MIME_PDF = "application/pdf";
  private static final String MIME_DOCX =
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

  private final ObjectMapper objectMapper;

  public TemplateIntrospectionResult introspect(String mimeType, byte[] payload) {
    if (MIME_PDF.equals(mimeType)) {
      return introspectPdf(payload);
    }
    if (MIME_DOCX.equals(mimeType)) {
      return introspectWord(payload);
    }
    return empty();
  }

  private TemplateIntrospectionResult introspectPdf(byte[] payload) {
    try (PDDocument document = Loader.loadPDF(payload)) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      ArrayNode fieldsNode = objectMapper.createArrayNode();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          if (field instanceof PDNonTerminalField) {
            continue;
          }
          String key = trimToNull(field.getFullyQualifiedName());
          if (key == null) {
            key = trimToNull(field.getPartialName());
          }
          if (key == null) {
            continue;
          }

          ObjectNode fieldNode = objectMapper.createObjectNode();
          fieldNode.put("key", key);
          fieldNode.put("type", resolvePdfFieldType(field));

          ArrayNode possibleValues = objectMapper.createArrayNode();
          for (String value : resolvePossibleValues(field)) {
            possibleValues.add(value);
          }
          fieldNode.set("possibleValues", possibleValues);
          fieldsNode.add(fieldNode);
        }
      }

      ObjectNode formMapNode = objectMapper.createObjectNode();
      formMapNode.set("fields", fieldsNode);
      return new TemplateIntrospectionResult(formMapNode.toString(), hasEsignAnchor(document));
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded PDF is not a valid form document", e);
    }
  }

  private TemplateIntrospectionResult introspectWord(byte[] payload) {
    Set<String> keys = extractPlaceholdersFromDocx(payload);
    ArrayNode fieldsNode = objectMapper.createArrayNode();
    for (String key : keys) {
      ObjectNode fieldNode = objectMapper.createObjectNode();
      fieldNode.put("key", key);
      fieldNode.put("type", "TEXT");
      fieldNode.set("possibleValues", objectMapper.createArrayNode());
      fieldsNode.add(fieldNode);
    }

    ObjectNode formMapNode = objectMapper.createObjectNode();
    formMapNode.set("fields", fieldsNode);
    return new TemplateIntrospectionResult(formMapNode.toString(), false);
  }

  private Set<String> extractPlaceholdersFromDocx(byte[] payload) {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(payload));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return extractPlaceholderKeys(extractor.getText());
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded Word document is not a valid .docx file", e);
    }
  }

  private static Set<String> extractPlaceholderKeys(String text) {
    Set<String> keys = new LinkedHashSet<>();
    addMatches(HANDLEBARS_PLACEHOLDER, text, keys);
    addMatches(EL_PLACEHOLDER, text, keys);
    return keys;
  }

  private static void addMatches(Pattern pattern, String text, Set<String> keys) {
    Matcher matcher = pattern.matcher(text == null ? "" : text);
    while (matcher.find()) {
      String key = trimToNull(matcher.group(1));
      if (key != null) {
        keys.add(key);
      }
    }
  }

  private static String resolvePdfFieldType(PDField field) {
    if (field instanceof PDCheckBox) {
      return "CHECKBOX";
    }
    if (field instanceof PDRadioButton) {
      return "RADIO";
    }
    if (field instanceof PDListBox) {
      return "SELECT";
    }
    if (field instanceof PDChoice) {
      return "SELECT";
    }
    if (field instanceof PDTextField textField) {
      return textField.isMultiline() ? "MULTILINE_TEXT" : "TEXT";
    }
    return "UNKNOWN";
  }

  private static List<String> resolvePossibleValues(PDField field) {
    if (field instanceof PDCheckBox checkBox) {
      String onValue = trimToNull(checkBox.getOnValue());
      if (onValue == null) {
        return List.of("false", "true");
      }
      return List.of("false", onValue);
    }
    if (field instanceof PDRadioButton radioButton) {
      return deduplicate(radioButton.getExportValues());
    }
    if (field instanceof PDChoice choice) {
      return deduplicate(choice.getOptionsDisplayValues());
    }
    return List.of();
  }

  private boolean hasEsignAnchor(PDDocument document) throws IOException {
    String text = new PDFTextStripper().getText(document).toLowerCase(Locale.ROOT);
    return PDF_ESIGN_ANCHORS.stream().anyMatch(text::contains);
  }

  private static List<String> deduplicate(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      String normalized = trimToNull(value);
      if (normalized != null) {
        unique.add(normalized);
      }
    }
    return new ArrayList<>(unique);
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private TemplateIntrospectionResult empty() {
    ObjectNode formMapNode = objectMapper.createObjectNode();
    formMapNode.set("fields", objectMapper.createArrayNode());
    return new TemplateIntrospectionResult(formMapNode.toString(), false);
  }

  public record TemplateIntrospectionResult(String formMapJson, boolean esignable) {}
}
