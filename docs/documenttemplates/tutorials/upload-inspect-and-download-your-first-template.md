# Tutorial: Upload, Inspect, and Download Your First Template

## Outcome

Upload a PDF template, verify extracted field metadata, search it, download it, and delete it.

## Prerequisites

- Running backend application
- Authenticated admin session for `/api/admin/document-templates`
- CSRF token/header available for mutating requests in your environment
- A local PDF file (example: `Client Intake Form.pdf`)

## Step 1: Upload a template

```bash
curl -i -X POST "http://localhost:8080/api/admin/document-templates" \
  -H "X-CSRF-TOKEN: <csrf-token>" \
  -b "<session-cookie>" \
  -F "file=@Client Intake Form.pdf;type=application/pdf" \
  -F "description=Onboarding package"
```

Expected response:

- Status `201 Created`
- JSON body with a new `id`

## Step 2: Read template metadata

```bash
curl -s "http://localhost:8080/api/admin/document-templates/<id>" \
  -b "<session-cookie>"
```

Check these fields:

- `name`
- `mimeType`
- `tenantId`
- `esignable`
- `formMap.fields[]`

## Step 3: Search by name

```bash
curl -s "http://localhost:8080/api/admin/document-templates/search?nameContains=intake&page=0&size=25" \
  -b "<session-cookie>"
```

Expected response:

- `items` contains your uploaded template
- Pagination fields (`page`, `size`, `totalElements`) are present

## Step 4: Download the template bytes

```bash
curl -i "http://localhost:8080/api/admin/document-templates/<id>/download" \
  -b "<session-cookie>" \
  -o downloaded-template.pdf
```

Check headers:

- `Content-Type` matches template MIME type
- `Content-Disposition` uses attachment filename

## Step 5: Delete the template

```bash
curl -i -X DELETE "http://localhost:8080/api/admin/document-templates/<id>" \
  -H "X-CSRF-TOKEN: <csrf-token>" \
  -H "Idempotency-Key: <uuid>" \
  -b "<session-cookie>"
```

Expected response:

- Status `204 No Content`

## Verify

- `GET /api/admin/document-templates/<id>` now returns `404`
- Audit log contains `CREATE` and `DELETE` events for resource type `documenttemplates`

## Next Steps

- Configure request-driven generation mappings with [How-to: Configure Request-Driven Document Generation by templateKey](../how-to/configure-request-driven-document-generation-by-template-key.md).
- Use full contracts in [Document Templates Reference](../reference/document-templates-reference.md).
