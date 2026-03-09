package com.gurch.sandbox.documenttemplates.internal;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.springframework.stereotype.Service;

/** Renders fillable PDF templates and flattens the resulting AcroForm. */
@Service
public class DocumentTemplatePdfGenerationService {

  public byte[] renderAsPdf(byte[] sourceBytes, Map<String, Object> fields) {
    Map<String, String> fieldValues = toFieldValues(fields);
    try (PDDocument document = Loader.loadPDF(sourceBytes);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          String fieldName = firstNonBlank(field.getFullyQualifiedName(), field.getPartialName());
          if (fieldName == null) {
            continue;
          }
          String value = fieldValues.get(fieldName);
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

  private static void applyPdfFieldValue(PDField field, String value) throws IOException {
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
}
