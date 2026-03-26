package com.gurch.sandbox.documenttemplates.internal;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTBorder;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTJc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcBorders;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTcPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STJc;
import org.springframework.stereotype.Service;

/** Renders DOCX templates and converts the rendered output to PDF. */
@Service
public class DocumentTemplateDocxGenerationService {

  private static final String NON_BREAKING_SPACE = "\u00A0";
  private static final Configure DOCX_TEMPLATE_CONFIG = Configure.createDefault();
  private static final Pattern SCALAR_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
  private static final Pattern TABLE_PLACEHOLDER =
      Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_-]+)\\.([a-zA-Z0-9_.-]+)\\s*}}");

  public byte[] renderAsPdf(byte[] sourceBytes, Map<String, Object> fields) {
    byte[] renderedDocx = renderDocx(sourceBytes, fields);
    return convertDocxToPdf(prepareDocxForPdf(renderedDocx));
  }

  byte[] renderDocx(byte[] sourceBytes, Map<String, Object> fields) {
    Map<String, Object> renderData = normalizeDocxData(fields);
    byte[] expandedDocx = expandTemplateTables(sourceBytes, renderData);
    try (XWPFTemplate template =
            XWPFTemplate.compile(new ByteArrayInputStream(expandedDocx), DOCX_TEMPLATE_CONFIG);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      template.render(renderData);
      template.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to render DOCX template", e);
    }
  }

  private static Map<String, Object> normalizeDocxData(Map<String, Object> fields) {
    if (fields == null || fields.isEmpty()) {
      return Map.of();
    }
    Map<String, Object> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      Object value = entry.getValue();
      if (isStructuredTableConfig(value)) {
        value = ((Map<?, ?>) value).get("rows");
      }
      putNested(normalized, entry.getKey(), value);
    }
    return normalized;
  }

  private static boolean isStructuredTableConfig(Object value) {
    if (!(value instanceof Map<?, ?> map)) {
      return false;
    }
    return map.get("rows") instanceof List<?>;
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    return String.valueOf(value);
  }

  @SuppressWarnings("unchecked")
  private static void putNested(Map<String, Object> root, String key, Object value) {
    if (key == null || key.isBlank() || !key.contains(".")) {
      root.put(key, value);
      return;
    }
    List<String> parts = splitPathSegments(key);
    Map<String, Object> cursor = root;
    for (int i = 0; i < parts.size() - 1; i++) {
      String part = parts.get(i);
      Object existing = cursor.get(part);
      if (existing instanceof Map<?, ?> map) {
        cursor = (Map<String, Object>) map;
        continue;
      }
      Map<String, Object> child = new LinkedHashMap<>();
      cursor.put(part, child);
      cursor = child;
    }
    cursor.put(parts.get(parts.size() - 1), value);
  }

  private static List<String> splitPathSegments(String key) {
    List<String> parts = new ArrayList<>();
    int start = 0;
    for (int i = 0; i < key.length(); i++) {
      if (key.charAt(i) == '.') {
        parts.add(key.substring(start, i));
        start = i + 1;
      }
    }
    parts.add(key.substring(start));
    return parts;
  }

  private void normalizeTemplatePlaceholders(XWPFDocument document) {
    for (XWPFParagraph paragraph : document.getParagraphs()) {
      normalizeParagraphPlaceholders(paragraph);
    }
    for (XWPFTable table : document.getTables()) {
      normalizeTablePlaceholders(table);
    }
  }

  private void normalizeTablePlaceholders(XWPFTable table) {
    for (XWPFTableRow row : table.getRows()) {
      for (XWPFTableCell cell : row.getTableCells()) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
          normalizeParagraphPlaceholders(paragraph);
        }
        for (XWPFTable nestedTable : cell.getTables()) {
          normalizeTablePlaceholders(nestedTable);
        }
      }
    }
  }

  private void normalizeParagraphPlaceholders(XWPFParagraph paragraph) {
    for (XWPFRun run : paragraph.getRuns()) {
      String text = run.getText(0);
      if (text == null) {
        continue;
      }
      String normalized = normalizeScalarPlaceholders(text);
      if (!normalized.equals(text)) {
        run.setText(normalized, 0);
      }
    }
  }

  private String normalizeScalarPlaceholders(String text) {
    Matcher matcher = SCALAR_PLACEHOLDER.matcher(text);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(result, Matcher.quoteReplacement("{{" + matcher.group(1) + "}}"));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  private byte[] expandTemplateTables(byte[] sourceBytes, Map<String, Object> renderData) {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(sourceBytes));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      normalizeTemplatePlaceholders(document);
      for (XWPFTable table : document.getTables()) {
        expandTemplateTable(table, renderData);
      }
      document.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to expand DOCX table template", e);
    }
  }

  private void expandTemplateTable(XWPFTable table, Map<String, Object> renderData) {
    for (int rowIndex = table.getNumberOfRows() - 1; rowIndex >= 0; rowIndex--) {
      XWPFTableRow templateRow = table.getRow(rowIndex);
      TableTemplateBinding binding = findTableBinding(templateRow);
      if (binding == null) {
        continue;
      }

      List<Map<String, Object>> rows = tableRows(renderData.get(binding.tableKey()));
      for (int i = rows.size() - 1; i >= 0; i--) {
        XWPFTableRow copiedRow = new XWPFTableRow((CTRow) templateRow.getCtRow().copy(), table);
        replaceRowPlaceholders(copiedRow, binding.tableKey(), rows.get(i));
        table.addRow(copiedRow, rowIndex + 1);
      }
      table.removeRow(rowIndex);
    }
  }

  private TableTemplateBinding findTableBinding(XWPFTableRow row) {
    String detectedTableKey = null;
    for (XWPFTableCell cell : row.getTableCells()) {
      Matcher matcher = TABLE_PLACEHOLDER.matcher(cell.getText());
      boolean found = false;
      while (matcher.find()) {
        found = true;
        String tableKey = matcher.group(1);
        if (detectedTableKey == null) {
          detectedTableKey = tableKey;
          continue;
        }
        if (!detectedTableKey.equals(tableKey)) {
          return null;
        }
      }
      if (!found && cell.getText().contains("{{")) {
        return null;
      }
    }
    if (detectedTableKey == null) {
      return null;
    }
    return new TableTemplateBinding(detectedTableKey);
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> tableRows(Object value) {
    if (!(value instanceof List<?> rows)) {
      return List.of();
    }
    List<Map<String, Object>> normalizedRows = new ArrayList<>();
    for (Object row : rows) {
      if (row instanceof Map<?, ?> rowMap) {
        normalizedRows.add((Map<String, Object>) rowMap);
      }
    }
    return normalizedRows;
  }

  private void replaceRowPlaceholders(
      XWPFTableRow row, String tableKey, Map<String, Object> rowValues) {
    for (XWPFTableCell cell : row.getTableCells()) {
      for (XWPFParagraph paragraph : cell.getParagraphs()) {
        replaceParagraphPlaceholders(paragraph, tableKey, rowValues);
      }
    }
  }

  private void replaceParagraphPlaceholders(
      XWPFParagraph paragraph, String tableKey, Map<String, Object> rowValues) {
    String originalText = paragraph.getText();
    if (originalText == null || originalText.isBlank()) {
      return;
    }
    String replacedText = replaceTablePlaceholders(originalText, tableKey, rowValues);
    if (replacedText.equals(originalText)) {
      return;
    }
    int runCount = paragraph.getRuns().size();
    for (int i = runCount - 1; i >= 1; i--) {
      paragraph.removeRun(i);
    }
    XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
    run.setText(replacedText, 0);
  }

  private String replaceTablePlaceholders(
      String text, String tableKey, Map<String, Object> rowValues) {
    Matcher matcher = TABLE_PLACEHOLDER.matcher(text);
    StringBuffer result = new StringBuffer();
    while (matcher.find()) {
      if (!tableKey.equals(matcher.group(1))) {
        continue;
      }
      String columnPath = matcher.group(2);
      String replacement = stringValue(resolvePathValue(rowValues, columnPath));
      matcher.appendReplacement(result, Matcher.quoteReplacement(replacement == null ? "" : replacement));
    }
    matcher.appendTail(result);
    return result.toString();
  }

  @SuppressWarnings("unchecked")
  private static Object resolvePathValue(Map<String, Object> rowValues, String columnPath) {
    if (rowValues.containsKey(columnPath)) {
      return rowValues.get(columnPath);
    }
    Object current = rowValues;
    for (String segment : splitPathSegments(columnPath)) {
      if (!(current instanceof Map<?, ?> currentMap) || !currentMap.containsKey(segment)) {
        return "";
      }
      current = ((Map<String, Object>) currentMap).get(segment);
    }
    return current;
  }

  private byte[] convertDocxToPdf(byte[] renderedDocx) {
    try (ByteArrayInputStream inputStream = new ByteArrayInputStream(renderedDocx);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      WordprocessingMLPackage wordprocessingMLPackage = WordprocessingMLPackage.load(inputStream);
      Docx4J.toPDF(wordprocessingMLPackage, outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      throw new IllegalStateException("Unable to convert rendered DOCX to PDF", e);
    }
  }

  byte[] prepareDocxForPdf(byte[] renderedDocx) {
    try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(renderedDocx));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      ensureVisibleBlankParagraphs(document);
      document.write(outputStream);
      return outputStream.toByteArray();
    } catch (IOException e) {
      throw new IllegalArgumentException("Unable to prepare DOCX for PDF conversion", e);
    }
  }

  private void ensureVisibleBlankParagraphs(XWPFDocument document) {
    for (XWPFParagraph paragraph : document.getParagraphs()) {
      ensureVisibleBlankParagraph(paragraph);
      normalizeParagraphForPdf(paragraph);
    }
    for (XWPFTable table : document.getTables()) {
      ensureVisibleBlankParagraphs(table);
    }
  }

  private void ensureVisibleBlankParagraphs(XWPFTable table) {
    for (XWPFTableRow row : table.getRows()) {
      for (XWPFTableCell cell : row.getTableCells()) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
          ensureVisibleBlankParagraph(paragraph);
          normalizeParagraphForPdf(paragraph);
        }
        normalizeCellBordersForPdf(cell);
        for (XWPFTable nestedTable : cell.getTables()) {
          ensureVisibleBlankParagraphs(nestedTable);
        }
      }
    }
  }

  private void ensureVisibleBlankParagraph(XWPFParagraph paragraph) {
    if (!paragraph.getText().isEmpty()) {
      return;
    }
    XWPFRun run = paragraph.getRuns().isEmpty() ? paragraph.createRun() : paragraph.getRuns().get(0);
    run.setText(NON_BREAKING_SPACE, 0);
  }

  private void normalizeParagraphForPdf(XWPFParagraph paragraph) {
    CTPPr paragraphProperties =
        paragraph.getCTP().isSetPPr() ? paragraph.getCTP().getPPr() : paragraph.getCTP().addNewPPr();
    CTJc justification =
        paragraphProperties.isSetJc() ? paragraphProperties.getJc() : paragraphProperties.addNewJc();
    if (STJc.END.equals(justification.getVal())) {
      justification.setVal(STJc.RIGHT);
      return;
    }
    if (STJc.START.equals(justification.getVal())) {
      justification.setVal(STJc.LEFT);
    }
  }

  private void normalizeCellBordersForPdf(XWPFTableCell cell) {
    CTTcPr cellProperties =
        cell.getCTTc().isSetTcPr() ? cell.getCTTc().getTcPr() : cell.getCTTc().addNewTcPr();
    CTTcBorders borders =
        cellProperties.isSetTcBorders()
            ? cellProperties.getTcBorders()
            : cellProperties.addNewTcBorders();

    if (borders.isSetStart() && !borders.isSetLeft()) {
      borders.setLeft((CTBorder) borders.getStart().copy());
    }
    if (borders.isSetEnd() && !borders.isSetRight()) {
      borders.setRight((CTBorder) borders.getEnd().copy());
    }
  }

  private record TableTemplateBinding(String tableKey) {}
}
