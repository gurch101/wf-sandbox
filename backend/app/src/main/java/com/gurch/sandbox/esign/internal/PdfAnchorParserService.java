package com.gurch.sandbox.esign.internal;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

@Service
class PdfAnchorParserService {

  private static final Pattern SIGNATURE_ANCHOR_PATTERN =
      Pattern.compile("/(s\\d+)/", Pattern.CASE_INSENSITIVE);
  private static final Pattern DATE_ANCHOR_PATTERN =
      Pattern.compile("/(d\\d+)/", Pattern.CASE_INSENSITIVE);

  PdfAnchorParseResult parse(byte[] payload) {
    try (PDDocument document = Loader.loadPDF(payload)) {
      String text = new PDFTextStripper().getText(document).toLowerCase(Locale.ROOT);
      return new PdfAnchorParseResult(findMatches(text, SIGNATURE_ANCHOR_PATTERN), findMatches(text, DATE_ANCHOR_PATTERN));
    } catch (IOException e) {
      throw new IllegalArgumentException("Uploaded PDF is not a valid PDF document", e);
    }
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
