package com.gurch.sandbox.esign.internal;

import java.util.Set;

record PdfAnchorParseResult(Set<String> signatureAnchorKeys, Set<String> dateAnchorKeys) {}
