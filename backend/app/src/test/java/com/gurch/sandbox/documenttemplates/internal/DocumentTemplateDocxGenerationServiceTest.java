package com.gurch.sandbox.documenttemplates.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocumentTemplateDocxGenerationServiceTest {

  private final DocumentTemplateDocxGenerationService service =
      new DocumentTemplateDocxGenerationService();

  @Test
  void shouldRenderSpacedPlaceholdersPreserveParagraphsAndKeepTableAlignment() throws IOException {
    byte[] template = loadTestResource("documenttemplates/introspect.docx");

    byte[] rendered =
        service.renderDocx(
            template,
            Map.of(
                "clientName", "Ada Lovelace",
                "holdings",
                    List.of(
                        Map.of("name", "AAPL", "quantity", 10, "marketValue", "1870.00"),
                        Map.of("name", "MSFT", "quantity", 5, "marketValue", "2060.00"))));

    try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(rendered))) {
      assertThat(document.getParagraphs()).hasSize(7);
      assertThat(document.getParagraphs().get(0).getText()).isEqualTo("Hi Ada Lovelace,");
      assertThat(document.getParagraphs().get(1).getText()).isEmpty();
      assertThat(document.getParagraphs().get(2).getText()).isEmpty();
      assertThat(document.getParagraphs().get(3).getText()).isEqualTo("Here are your holdings:");
      assertThat(document.getParagraphs().get(4).getText()).isEmpty();
      assertThat(document.getParagraphs().get(5).getText()).isEmpty();
      assertThat(document.getParagraphs().get(6).getText()).isEmpty();

      XWPFTable table = document.getTables().get(0);
      assertThat(table.getNumberOfRows()).isEqualTo(3);
      assertThat(table.getRow(0).getCell(0).getText()).isEqualTo("Security Name");
      assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("Quantity");
      assertThat(table.getRow(0).getCell(2).getText()).isEqualTo("Market Value");

      assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("AAPL");
      assertThat(table.getRow(1).getCell(1).getText()).isEqualTo("10");
      assertThat(table.getRow(1).getCell(2).getText()).isEqualTo("1870.00");
      assertThat(table.getRow(2).getCell(0).getText()).isEqualTo("MSFT");
      assertThat(table.getRow(2).getCell(1).getText()).isEqualTo("5");
      assertThat(table.getRow(2).getCell(2).getText()).isEqualTo("2060.00");

      assertThat(table.getRow(1).getCell(1).getParagraphs().get(0).getAlignment())
          .isIn(ParagraphAlignment.RIGHT, ParagraphAlignment.END);
      assertThat(table.getRow(1).getCell(2).getParagraphs().get(0).getAlignment())
          .isIn(ParagraphAlignment.RIGHT, ParagraphAlignment.END);
      assertThat(table.getRow(2).getCell(1).getParagraphs().get(0).getAlignment())
          .isIn(ParagraphAlignment.RIGHT, ParagraphAlignment.END);
      assertThat(table.getRow(2).getCell(2).getParagraphs().get(0).getAlignment())
          .isIn(ParagraphAlignment.RIGHT, ParagraphAlignment.END);
    }
  }

  @Test
  void shouldPreserveBlankParagraphsWhenPreparingDocxForPdf() throws IOException {
    byte[] template = loadTestResource("documenttemplates/introspect.docx");
    byte[] rendered =
        service.renderDocx(
            template,
            Map.of(
                "clientName", "Ada Lovelace",
                "holdings",
                    List.of(
                        Map.of("name", "AAPL", "quantity", 10, "marketValue", "1870.00"),
                        Map.of("name", "MSFT", "quantity", 5, "marketValue", "2060.00"))));

    byte[] prepared = service.prepareDocxForPdf(rendered);

    try (XWPFDocument document = new XWPFDocument(new java.io.ByteArrayInputStream(prepared))) {
      assertThat(document.getParagraphs().get(1).getText()).isEqualTo("\u00A0");
      assertThat(document.getParagraphs().get(2).getText()).isEqualTo("\u00A0");
      assertThat(document.getParagraphs().get(4).getText()).isEqualTo("\u00A0");
      assertThat(document.getParagraphs().get(5).getText()).isEqualTo("\u00A0");
      assertThat(document.getParagraphs().get(6).getText()).isEqualTo("\u00A0");
      assertThat(document.getTables().get(0).getRow(1).getCell(1).getParagraphs().get(0).getAlignment())
          .isEqualTo(ParagraphAlignment.RIGHT);
      assertThat(document.getTables().get(0).getRow(1).getCell(1).getCTTc().getTcPr().getTcBorders().isSetLeft())
          .isTrue();
      assertThat(document.getTables().get(0).getRow(1).getCell(2).getCTTc().getTcPr().getTcBorders().isSetRight())
          .isTrue();
    }
  }

  private byte[] loadTestResource(String path) throws IOException {
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(path)) {
      assertThat(inputStream).as("Missing test resource: %s", path).isNotNull();
      return inputStream.readAllBytes();
    }
  }
}
