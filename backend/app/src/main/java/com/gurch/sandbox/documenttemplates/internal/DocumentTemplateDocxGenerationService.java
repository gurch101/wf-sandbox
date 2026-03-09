package com.gurch.sandbox.documenttemplates.internal;

import com.deepoove.poi.XWPFTemplate;
import com.deepoove.poi.config.Configure;
import com.deepoove.poi.data.Cells;
import com.deepoove.poi.data.RowRenderData;
import com.deepoove.poi.data.Rows;
import com.deepoove.poi.data.Tables;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.docx4j.Docx4J;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.springframework.stereotype.Service;

/** Renders DOCX templates and converts the rendered output to PDF. */
@Service
public class DocumentTemplateDocxGenerationService {

  public byte[] renderAsPdf(byte[] sourceBytes, Map<String, Object> fields) {
    byte[] renderedDocx = renderDocxTemplate(sourceBytes, fields);
    return convertDocxToPdf(renderedDocx);
  }

  private byte[] renderDocxTemplate(byte[] sourceBytes, Map<String, Object> fields) {
    Map<String, Object> renderData = normalizeDocxData(fields);
    try (XWPFTemplate template =
            XWPFTemplate.compile(new ByteArrayInputStream(sourceBytes), Configure.createDefault());
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
        value = toTableRenderDataFromConfig((Map<?, ?>) value);
      } else if (isListOfMaps(value)) {
        value = toTableRenderData((List<?>) value);
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

  private static boolean isListOfMaps(Object value) {
    if (!(value instanceof List<?> list) || list.isEmpty()) {
      return false;
    }
    return list.stream().allMatch(item -> item instanceof Map<?, ?>);
  }

  private static Object toTableRenderData(List<?> rows) {
    if (rows.isEmpty()) {
      return rows;
    }
    Map<?, ?> firstRow = (Map<?, ?>) rows.get(0);
    List<String> headers = firstRow.keySet().stream().map(String::valueOf).toList();
    List<RowRenderData> tableRows = new ArrayList<>();
    tableRows.add(Rows.of(headers.toArray(String[]::new)).create());
    for (Object row : rows) {
      Map<?, ?> rowMap = (Map<?, ?>) row;
      String[] cells =
          headers.stream()
              .map(
                  header -> {
                    Object value = rowMap.containsKey(header) ? rowMap.get(header) : "";
                    return String.valueOf(value);
                  })
              .toArray(String[]::new);
      tableRows.add(Rows.of(cells).create());
    }
    return Tables.of(tableRows.toArray(RowRenderData[]::new)).create();
  }

  private static Object toTableRenderDataFromConfig(Map<?, ?> config) {
    Object rowsRaw = config.get("rows");
    if (!(rowsRaw instanceof List<?> rows)) {
      return config;
    }
    List<ColumnSpec> columns = parseColumns(config.get("headers"), rows);
    if (columns.isEmpty()) {
      return config;
    }

    List<RowRenderData> tableRows = new ArrayList<>();
    Rows.RowBuilder headerBuilder = Rows.of();
    for (ColumnSpec column : columns) {
      headerBuilder.addCell(Cells.of(column.label()).create());
    }
    tableRows.add(headerBuilder.create());

    for (Object row : rows) {
      if (!(row instanceof Map<?, ?> rowMap)) {
        continue;
      }
      Rows.RowBuilder rowBuilder = Rows.of();
      for (ColumnSpec column : columns) {
        Object raw = rowMap.containsKey(column.key()) ? rowMap.get(column.key()) : "";
        Cells.CellBuilder cellBuilder = Cells.of(String.valueOf(raw));
        switch (column.alignment()) {
          case "RIGHT" -> cellBuilder.horizontalRight();
          case "CENTER" -> cellBuilder.horizontalCenter();
          default -> cellBuilder.horizontalLeft();
        }
        rowBuilder.addCell(cellBuilder.create());
      }
      tableRows.add(rowBuilder.create());
    }
    return Tables.of(tableRows.toArray(RowRenderData[]::new)).create();
  }

  private static List<ColumnSpec> parseColumns(Object headersRaw, List<?> rows) {
    List<ColumnSpec> columns = new ArrayList<>();
    if (headersRaw instanceof List<?> headers && !headers.isEmpty()) {
      for (Object header : headers) {
        if (header instanceof Map<?, ?> headerMap) {
          String key = stringValue(headerMap.get("key"));
          if (key == null) {
            continue;
          }
          String label = stringValue(headerMap.get("label"));
          String alignment = normalizeAlignment(stringValue(headerMap.get("align")));
          columns.add(new ColumnSpec(key, label == null ? key : label, alignment));
          continue;
        }
        String key = stringValue(header);
        if (key != null) {
          columns.add(new ColumnSpec(key, key, "LEFT"));
        }
      }
      if (!columns.isEmpty()) {
        return columns;
      }
    }

    if (rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> firstRow)) {
      return List.of();
    }
    for (Object key : firstRow.keySet()) {
      String keyText = stringValue(key);
      if (keyText != null) {
        columns.add(new ColumnSpec(keyText, keyText, "LEFT"));
      }
    }
    return columns;
  }

  private static String normalizeAlignment(String alignment) {
    if (alignment == null) {
      return "LEFT";
    }
    String normalized = alignment.trim().toUpperCase(Locale.ROOT);
    if (normalized.equals("RIGHT") || normalized.equals("CENTER")) {
      return normalized;
    }
    return "LEFT";
  }

  private static String stringValue(Object value) {
    if (value == null) {
      return null;
    }
    String text = String.valueOf(value).trim();
    return text.isEmpty() ? null : text;
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

  private record ColumnSpec(String key, String label, String alignment) {}
}
