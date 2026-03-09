package com.gurch.sandbox.esign.internal;

import java.io.IOException;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
class PdfAnchorMarkerResolver {

  private static final Pattern S1 = Pattern.compile("\\bs1\\b");
  private static final Pattern S2 = Pattern.compile("\\bs2\\b");
  private static final Pattern D1 = Pattern.compile("\\bd1\\b");
  private static final Pattern D2 = Pattern.compile("\\bd2\\b");

  Set<AnchorToken> resolve(byte[] pdfBytes) {
    try (PDDocument document = Loader.loadPDF(pdfBytes)) {
      String text = new PDFTextStripper().getText(document).toLowerCase(Locale.ROOT);
      EnumSet<AnchorToken> tokens = EnumSet.noneOf(AnchorToken.class);
      if (S1.matcher(text).find()) {
        tokens.add(AnchorToken.S1);
      }
      if (S2.matcher(text).find()) {
        tokens.add(AnchorToken.S2);
      }
      if (D1.matcher(text).find()) {
        tokens.add(AnchorToken.D1);
      }
      if (D2.matcher(text).find()) {
        tokens.add(AnchorToken.D2);
      }
      return tokens;
    } catch (IOException e) {
      throw new IllegalArgumentException("documentPdf is not a valid PDF", e);
    }
  }
}
