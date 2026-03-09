# How-to: Configure Request-Driven Document Generation by templateKey

## Goal

Generate one merged PDF for one or more requests by mapping request payload paths to template field
keys.

## Prerequisites

- Templates uploaded with stable `templateKey` values
- Request type configuration that supports `documentGeneration.documents`
- A request payload with scalar values at the mapped paths
- Access to `DocumentTemplateApi.generateFromRequests(...)`

## Procedure

1. Upload templates and set `templateKey` values.

Example keys:

- `bundle-fillable` for a fillable PDF
- `bundle-word` for a Word template

2. Configure request type mappings in `configJson`:

```json
{
  "documentGeneration": {
    "documents": [
      {
        "templateKey": "bundle-fillable",
        "fieldBindings": {
          "clientName": "payload.client.firstName",
          "consent": "payload.flags.consent",
          "state": "payload.address.state"
        }
      },
      {
        "templateKey": "bundle-word",
        "fieldBindings": {
          "client.firstName": "payload.client.firstName",
          "client.lastName": "payload.client.lastName"
        }
      }
    ]
  }
}
```

3. Create one or more requests for that request type.
4. Call generation in request order:

```java
DocumentTemplateDownload output =
    documentTemplateApi.generateFromRequests(
        new DocumentTemplateGenerateFromRequestsRequest(List.of(requestOneId, requestTwoId)));
```

5. Stream `output.getContentStream()` to a `.pdf` file.

## Validation

- Output MIME type is `application/pdf`
- Output contains rendered content from all mapped templates
- Request order is preserved in the merged PDF
- Tenant-scoped template overrides are applied before global templates

## Troubleshooting

- `Missing required payload path ...`:
  - A `fieldBindings` payload path does not resolve to a scalar value.
- `Mapped field key ... does not exist in templateKey ...`:
  - Mapping key is not present in template `formMap.fields`.
- `No template found for key ...`:
  - Required mapping has no tenant/global template match.
- `Multiple tenant templates found for key ... with no tenant context`:
  - Resolve call is happening without tenant scope and key is ambiguous.
