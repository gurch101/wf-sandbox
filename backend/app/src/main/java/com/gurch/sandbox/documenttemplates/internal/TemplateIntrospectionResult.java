package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.DocumentTemplateEsignAnchorMetadata;
import com.gurch.sandbox.documenttemplates.DocumentTemplateFormMap;

public record TemplateIntrospectionResult(
    DocumentTemplateFormMap formMap,
    DocumentTemplateEsignAnchorMetadata esignAnchorMetadata,
    boolean esignable) {}
