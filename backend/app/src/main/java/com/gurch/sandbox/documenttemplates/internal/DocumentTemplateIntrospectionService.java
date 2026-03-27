package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.DocumentTemplateEsignAnchorMetadata;
import com.gurch.sandbox.documenttemplates.DocumentTemplateFormField;
import com.gurch.sandbox.documenttemplates.DocumentTemplateFormFieldType;
import com.gurch.sandbox.documenttemplates.DocumentTemplateFormMap;
import com.gurch.sandbox.documenttemplates.DocumentTemplateSharedErrorCode;
import com.gurch.sandbox.pdfutils.PdfAnchorParseResult;
import com.gurch.sandbox.pdfutils.PdfAnchorParser;
import com.gurch.sandbox.web.ValidationErrorException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDPushButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

@Service
public class DocumentTemplateIntrospectionService {

  private static final Pattern DOCX_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
  private static final Pattern DOCX_SIGNATURE_ANCHOR_PATTERN =
      Pattern.compile("/(s\\d+)/", Pattern.CASE_INSENSITIVE);
  private static final Pattern DOCX_DATE_ANCHOR_PATTERN =
      Pattern.compile("/(d\\d+)/", Pattern.CASE_INSENSITIVE);

  public TemplateIntrospectionResult introspect(String mimeType, byte[] payload) {
    if (DocumentTemplateMimeTypes.PDF.equals(mimeType)) {
      return introspectPdf(payload);
    }
    if (DocumentTemplateMimeTypes.DOCX.equals(mimeType)) {
      return introspectWord(payload);
    }
    throw ValidationErrorException.of(DocumentTemplateSharedErrorCode.UNSUPPORTED_FILE_TYPE);
  }

  private TemplateIntrospectionResult introspectPdf(byte[] payload) {
    try (PDDocument document = Loader.loadPDF(payload)) {
      PDAcroForm acroForm = document.getDocumentCatalog().getAcroForm();
      List<DocumentTemplateFormField> fields = new ArrayList<>();
      if (acroForm != null) {
        for (PDField field : acroForm.getFieldTree()) {
          if (field instanceof PDNonTerminalField || field instanceof PDPushButton) {
            continue;
          }
          String key = StringUtils.trimToNull(field.getFullyQualifiedName());
          if (key == null) {
            key = StringUtils.trimToNull(field.getPartialName());
          }
          if (key == null) {
            continue;
          }

          fields.add(
              new DocumentTemplateFormField(
                  key, resolvePdfFieldType(field), resolvePossibleValues(field)));
        }
      }

      DocumentTemplateEsignAnchorMetadata esignAnchorMetadata = parseEsignAnchorMetadata(document);
      return new TemplateIntrospectionResult(
          new DocumentTemplateFormMap(fields),
          esignAnchorMetadata,
          esignAnchorMetadata.isEsignable());
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded PDF is not a valid form document", e);
    }
  }

  private TemplateIntrospectionResult introspectWord(byte[] payload) {
    String text = extractTextFromDocx(payload);
    Set<String> keys = extractPlaceholderKeys(text);
    List<DocumentTemplateFormField> fields = new ArrayList<>();
    for (String key : keys) {
      fields.add(new DocumentTemplateFormField(key, DocumentTemplateFormFieldType.TEXT, List.of()));
    }
    DocumentTemplateEsignAnchorMetadata esignAnchorMetadata = parseEsignAnchorMetadata(text);
    return new TemplateIntrospectionResult(
        new DocumentTemplateFormMap(fields),
        esignAnchorMetadata,
        esignAnchorMetadata.isEsignable());
  }

  private String extractTextFromDocx(byte[] payload) {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(payload));
        XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
      return extractor.getText();
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded Word document is not a valid .docx file", e);
    }
  }

  private static Set<String> extractPlaceholderKeys(String text) {
    Set<String> keys = new LinkedHashSet<>();
    addMatches(DOCX_PLACEHOLDER, text, keys);
    return keys;
  }

  private static void addMatches(Pattern pattern, String text, Set<String> keys) {
    Matcher matcher = pattern.matcher(text == null ? "" : text);
    while (matcher.find()) {
      String key = StringUtils.trimToNull(matcher.group(1));
      if (key != null) {
        keys.add(key);
      }
    }
  }

  private static DocumentTemplateFormFieldType resolvePdfFieldType(PDField field) {
    if (field instanceof PDCheckBox) {
      return DocumentTemplateFormFieldType.CHECKBOX;
    }
    if (field instanceof PDRadioButton) {
      return DocumentTemplateFormFieldType.RADIO;
    }
    if (field instanceof PDListBox) {
      return DocumentTemplateFormFieldType.SELECT;
    }
    if (field instanceof PDChoice) {
      return DocumentTemplateFormFieldType.SELECT;
    }
    if (field instanceof PDTextField textField) {
      return textField.isMultiline()
          ? DocumentTemplateFormFieldType.MULTILINE_TEXT
          : DocumentTemplateFormFieldType.TEXT;
    }
    return DocumentTemplateFormFieldType.UNKNOWN;
  }

  private static List<String> resolvePossibleValues(PDField field) {
    if (field instanceof PDCheckBox) {
      return List.of("false", "true");
    }
    if (field instanceof PDRadioButton radioButton) {
      List<String> exportValues = deduplicate(radioButton.getExportValues());
      if (!exportValues.isEmpty()) {
        return exportValues;
      }
      return deduplicate(radioButton.getOnValues());
    }
    if (field instanceof PDChoice choice) {
      return deduplicate(choice.getOptionsDisplayValues());
    }
    return List.of();
  }

  private DocumentTemplateEsignAnchorMetadata parseEsignAnchorMetadata(PDDocument document)
      throws IOException {
    PdfAnchorParseResult result = PdfAnchorParser.parse(document);
    return new DocumentTemplateEsignAnchorMetadata(
        deduplicate(result.signatureAnchorKeys()), deduplicate(result.dateAnchorKeys()));
  }

  private DocumentTemplateEsignAnchorMetadata parseEsignAnchorMetadata(String text) {
    return new DocumentTemplateEsignAnchorMetadata(
        deduplicate(findMatches(text, DOCX_SIGNATURE_ANCHOR_PATTERN)),
        deduplicate(findMatches(text, DOCX_DATE_ANCHOR_PATTERN)));
  }

  private static Set<String> findMatches(String text, Pattern pattern) {
    LinkedHashSet<String> matches = new LinkedHashSet<>();
    Matcher matcher = pattern.matcher(text == null ? "" : text);
    while (matcher.find()) {
      String match = StringUtils.trimToNull(matcher.group(1));
      if (match != null) {
        matches.add(match.toLowerCase(java.util.Locale.ROOT));
      }
    }
    return matches;
  }

  private static List<String> deduplicate(List<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      String normalized = StringUtils.trimToNull(value);
      if (normalized != null) {
        unique.add(normalized);
      }
    }
    return new ArrayList<>(unique);
  }

  private static List<String> deduplicate(Set<String> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> unique = new LinkedHashSet<>();
    for (String value : values) {
      String normalized = StringUtils.trimToNull(value);
      if (normalized != null) {
        unique.add(normalized);
      }
    }
    return new ArrayList<>(unique);
  }
}
