package com.gurch.sandbox.pdfutils;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/** Utility for extracting e-sign anchor keys from PDF documents. */
public final class PdfAnchorParser {

  private static final Pattern SIGNATURE_ANCHOR_PATTERN =
      Pattern.compile("/(s\\d+)/", Pattern.CASE_INSENSITIVE);
  private static final Pattern DATE_ANCHOR_PATTERN =
      Pattern.compile("/(d\\d+)/", Pattern.CASE_INSENSITIVE);

  private PdfAnchorParser() {}

  /**
   * Parses e-sign anchors from a PDF payload.
   *
   * @param payload PDF content bytes
   * @return parsed anchor keys
   */
  public static PdfAnchorParseResult parse(byte[] payload) {
    try (PDDocument document = Loader.loadPDF(payload)) {
      return parse(document);
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded PDF is not a valid PDF document", e);
    }
  }

  /**
   * Parses e-sign anchors from an already-open PDF document.
   *
   * @param document open PDF document
   * @return parsed anchor keys
   * @throws IOException when PDF text extraction fails
   */
  public static PdfAnchorParseResult parse(PDDocument document) throws IOException {
    String text = new PDFTextStripper().getText(document).toLowerCase(Locale.ROOT);
    return new PdfAnchorParseResult(
        findMatches(text, SIGNATURE_ANCHOR_PATTERN), findMatches(text, DATE_ANCHOR_PATTERN));
  }

  private static Set<String> findMatches(String text, Pattern pattern) {
    LinkedHashSet<String> matches = new LinkedHashSet<>();
    Matcher matcher = pattern.matcher(text == null ? "" : text);
    while (matcher.find()) {
      matches.add(matcher.group(1).toLowerCase(Locale.ROOT));
    }
    return matches;
  }
}
