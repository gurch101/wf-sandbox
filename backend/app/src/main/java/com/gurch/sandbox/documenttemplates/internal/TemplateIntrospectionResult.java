package com.gurch.sandbox.documenttemplates.internal;

import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateEsignAnchorMetadata;
import com.gurch.sandbox.documenttemplates.dto.DocumentTemplateFormMap;

public record TemplateIntrospectionResult(
    DocumentTemplateFormMap formMap,
    DocumentTemplateEsignAnchorMetadata esignAnchorMetadata,
    boolean esignable) {}
