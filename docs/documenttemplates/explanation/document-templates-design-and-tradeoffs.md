# Explanation: Document Templates Design and Tradeoffs

## Context

`documenttemplates` centralizes file-backed templates used by admins and request workflows. The
module supports both manual template management (upload/search/download/delete) and automated PDF
bundle generation from request payload data.

## Design

Main components:

- `DocumentTemplateController`: REST entrypoints for CRUD/search/download
- `DocumentTemplateApi` (`DefaultDocumentTemplateService`): domain orchestration and access rules
- `DocumentTemplateIntrospectionService`: extracts template field metadata and e-sign hints
- `DocumentTemplateGenerationService`: renders PDF/Word templates and merges into one PDF
- `RequestDocumentGenerationService`: resolves request-type mappings and payload bindings
- `DocumentTemplateRepository`: persistence of metadata in `document_templates`
- `StorageApi`: persistence of bytes in storage backend

Pipeline on upload:

1. Validate request shape, size limit, and tenant access.
2. Normalize MIME type from declared type/filename.
3. Read payload bytes.
4. Introspect fields into `formMap` and set `esignable`.
5. Write bytes to storage and metadata to DB.
6. Emit audit `CREATE` event.

Pipeline on generation:

1. Resolve templates (by id or by request mapping/templateKey).
2. Resolve/validate fields.
3. Render each template to PDF:
- PDF templates: fill AcroForm fields, then flatten.
- Word templates: extract text, replace placeholders, draw text into a PDF.
4. Merge rendered PDFs in input order.

## Tenant Access Model

Read/download/delete/search apply tenant scope:

- Tenant user sees tenant-owned templates plus global templates.
- Cross-tenant records are hidden as `404`.
- Upload with `tenantId` must match authenticated tenant scope.

Request-driven generation applies tenant-aware template resolution:

- Prefer tenant template for a `templateKey`.
- Fallback to global template if tenant template is missing.

## Tradeoffs

Pros:

- Single reusable module for template storage, introspection, and generation.
- Supports both form-based PDFs and placeholder-based Word templates.
- Request-type mappings decouple document composition from controller logic.
- Tenant-aware resolution enables global defaults plus tenant overrides.

Cons:

- Word rendering is text-based; original Word layout/styling is not preserved.
- E-sign detection is anchor-text heuristic (`s1`, `s2`, `d1`, `d2`), not semantic signature parsing.
- Validation happens at runtime against `formMap`; mapping mistakes fail late during generation.
- Storage failures are surfaced as runtime `500` errors and require operational handling.

## Why This Shape

The module optimizes for predictable backend composition and workflow integration instead of full
fidelity document layout conversion. For generated output, consistent PDF delivery and deterministic
mapping behavior were prioritized over preserving advanced Word formatting features.

## Related Docs

- Start with [Tutorial: Upload, Inspect, and Download Your First Template](../tutorials/upload-inspect-and-download-your-first-template.md)
- Use task steps in [How-to: Configure Request-Driven Document Generation by templateKey](../how-to/configure-request-driven-document-generation-by-template-key.md)
- Check exact contracts in [Document Templates Reference](../reference/document-templates-reference.md)
