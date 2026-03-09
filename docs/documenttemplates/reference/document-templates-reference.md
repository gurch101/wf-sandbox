# Reference: Document Templates

## Scope

The module provides:

- HTTP APIs for upload, get by id, download, search, and delete
- Service APIs for template-based PDF generation and request-driven generation
- Persistent metadata in `document_templates` plus file content in `StorageApi`

## HTTP Endpoints

Base path: `/api/admin/document-templates`

### `POST /api/admin/document-templates` (multipart)

Consumes `multipart/form-data`:

- `file` (required): uploaded PDF/DOC/DOCX
- `templateKey` (optional): stable key for request-driven generation
- `name` (optional): display name override; defaults to original filename
- `description` (optional)
- `tenantId` (optional): null means global template

Response:

- `201 Created`
- Body: `CreateResponse { id }`

Notes:

- Endpoint is marked `@NotIdempotent` because multipart body is streamed.

### `GET /api/admin/document-templates/{id}`

Response:

- `200 OK` with `DocumentTemplateResponse`
- `404` when missing or out of tenant scope

### `GET /api/admin/document-templates/{id}/download`

Response:

- `200 OK` with streamed bytes
- Headers include `Content-Type`, `Content-Disposition`, `Content-Length`
- `404` when missing, out of tenant scope, or stored bytes are missing

### `GET /api/admin/document-templates/search`

Query params (`DocumentTemplateSearchCriteria` + pagination):

- `nameContains` (optional, case-insensitive contains)
- `tenantId` (optional exact match)
- `page` (optional, default `0`)
- `size` (optional, default `25`, max `100`)

Response:

- `200 OK` with `PagedResponse<DocumentTemplateResponse>`

Tenant filter behavior:

- Authenticated tenant users only see records where `tenant_id = currentTenantId` or `tenant_id IS NULL`.

### `DELETE /api/admin/document-templates/{id}`

Response:

- `204 No Content`
- `404` when missing or out of tenant scope

Effects:

- Deletes stored bytes via `StorageApi`
- Deletes metadata row
- Writes audit `DELETE` for resource type `documenttemplates`

## Service API (`DocumentTemplateApi`)

- `DocumentTemplateResponse upload(DocumentTemplateUploadRequest request)`
- `Optional<DocumentTemplateResponse> findById(Long id)`
- `DocumentTemplateDownload download(Long id)`
- `DocumentTemplateDownload generate(DocumentTemplateGenerateRequest request)`
- `DocumentTemplateDownload generateFromRequests(DocumentTemplateGenerateFromRequestsRequest request)`
- `PagedResponse<DocumentTemplateResponse> search(DocumentTemplateSearchCriteria criteria)`
- `void deleteById(Long id)`

`generate(...)` and `generateFromRequests(...)` return:

- `name = "generated-document-bundle.pdf"`
- `mimeType = "application/pdf"`

## Core DTOs

### `DocumentTemplateResponse`

- `id: Long`
- `templateKey: String?`
- `name: String`
- `description: String?`
- `mimeType: String`
- `contentSize: Long`
- `checksumSha256: String`
- `tenantId: Integer?`
- `formMap: JsonNode` (`{ "fields": [...] }`)
- `esignable: boolean`
- `createdAt: Instant`
- `updatedAt: Instant`
- `version: Long`

### `DocumentTemplateGenerateRequest`

- `documents: List<GenerateInput>` (required, non-empty)

`GenerateInput`:

- `documentTemplateId: Long` (required)
- `fields: Map<String, Object>` (optional)

### `DocumentTemplateGenerateFromRequestsRequest`

- `requestIds: List<Long>` (required, non-empty, no null entries)

## File Type and Introspection Rules

Accepted MIME types/extensions:

- `application/pdf` (`.pdf`)
- `application/msword` (`.doc`)
- `application/vnd.openxmlformats-officedocument.wordprocessingml.document` (`.docx`)

Upload introspection:

- PDF: extracts AcroForm field keys/types/possible values into `formMap.fields`
- Word: extracts placeholders from `{{key}}` and `${key}` patterns into `formMap.fields`
- `esignable` is true for PDF text containing any of: `s1`, `s2`, `d1`, `d2`

## Request-Driven Mapping Config Shape

Expected path in request type config JSON:

- `documentGeneration.documents[]`

Each document entry:

- `templateKey: String` (required)
- `fieldBindings: { "templateFieldKey": "payload.path" }` (required)
- `enabled: boolean` (optional, default `true`)
- `required: boolean` (optional, default `true`)
- `tenantRules[]` (optional per-tenant overrides for `enabled` and `required`)

Template resolution order:

- With tenant context: tenant template first, then global template
- Without tenant context: global template first; if only one tenant template exists, use it; if multiple tenant templates match, throw ambiguity error

## Persistence Model

Table: `document_templates`

Core columns:

- `id` (`BIGINT`, identity, PK)
- `template_key` (`VARCHAR(200)`, nullable)
- `name` (`VARCHAR(255)`, required)
- `description` (`TEXT`, nullable)
- `mime_type` (`VARCHAR(255)`, required)
- `content_size` (`BIGINT`, required)
- `checksum_sha256` (`VARCHAR(64)`, required)
- `tenant_id` (`INTEGER`, nullable FK)
- `form_map_json` (`TEXT`, nullable)
- `esignable` (`BOOLEAN`, required)
- `storage_provider` (`VARCHAR(32)`, required)
- `storage_path` (`TEXT`, required, unique)
- `created_at`, `updated_at`, `created_by`, `updated_by`, `version`

Template key uniqueness:

- Unique per tenant for non-null `template_key`
- Unique globally for non-null `template_key` where `tenant_id IS NULL`

## Limits and Configuration

In `backend/app/src/main/resources/application.yaml`:

- `spring.servlet.multipart.max-file-size` default `25MB`
- `spring.servlet.multipart.max-request-size` default `25MB`
- `documenttemplates.upload.max-size-bytes` default `26214400`

When exceeded, API returns `413 Payload Too Large`.

## Error Behavior

Common validation/runtime errors:

- `400` invalid upload input (`file is required`, unsupported type, tenant mismatch)
- `404` template not found or inaccessible by tenant scope
- `413` multipart/service upload size limits
- `400` generation input errors (missing ids, missing payload bindings, invalid mapping)
- `500` storage read/write/delete failures (`IllegalStateException`)
