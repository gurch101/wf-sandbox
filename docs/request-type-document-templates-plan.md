# Versioned Request-Type Document Template Associations

## Summary
Link document templates to `request_type_versions`, not to requests or to templates themselves. Store the associations in `request_type_versions.config_json` as a versioned, declarative `documentTemplates` section.

Use typed associations and explicit field mappings:
- typed association: defines why/when the template is used
- explicit field mapping: defines how request-type data satisfies that template’s field map

Validate associations as a hard fail when a request type version is created or changed. A request type version may only reference a template if every required template field can be satisfied by a declared mapping from known request-type inputs.

## Key Changes

### 1. Add a versioned document-template config model to request types
Extend `request_type_versions.config_json` with a typed list like:

```json
{
  "documentTemplates": [
    {
      "templateId": 123,
      "role": "ESIGN_REQUIRED",
      "name": "eft-in-client-form",
      "taskDefinitionKey": null,
      "required": true,
      "fieldMappings": [
        {"templateField": "clientName", "sourceType": "REQUEST_PAYLOAD", "sourceKey": "request.clientName"},
        {"templateField": "account.number", "sourceType": "REQUEST_PAYLOAD", "sourceKey": "request.accountNumber"}
      ]
    },
    {
      "templateId": 456,
      "role": "USER_TASK_GENERATED",
      "name": "transfer-fax-cover-letter",
      "taskDefinitionKey": "ops-review-transfer",
      "required": true,
      "fieldMappings": [
        {"templateField": "advisorName", "sourceType": "REQUEST_PAYLOAD", "sourceKey": "request.advisorName"},
        {"templateField": "taskOutcome", "sourceType": "WORKFLOW_FIELD", "sourceKey": "workflow.latestTaskAction"}
      ]
    }
  ]
}
```

Defaults and rules:
- associations are immutable-by-version because they live on `request_type_versions`
- `name` is a stable config key within a request type version, used for diagnostics and future workflow references
- `taskDefinitionKey` is required only for `USER_TASK_GENERATED`
- `fieldMappings` is explicit and complete; no hidden name-based auto-binding in persisted config
- source keys must come from the request type’s known input universe

### 2. Introduce document-association roles and source types
Use these role values initially:
- `ESIGN_REQUIRED`
- `USER_TASK_GENERATED`

Use these mapping source types initially:
- `REQUEST_PAYLOAD`
- `COMPUTED_FIELD`
- `WORKFLOW_FIELD`
- `LITERAL`

Behavior:
- `ESIGN_REQUIRED` means the template is part of the request type’s required e-sign package
- `USER_TASK_GENERATED` means the template may be generated during a specific workflow user task identified by `taskDefinitionKey`
- `LITERAL` supports fixed values where needed without writing code
- no handler-owned hidden mapping logic in v1; all template-field population rules are visible in config

### 3. Validate compatibility when request types are configured
Add request-type version validation that runs during create/change and rejects invalid document-template associations.

Validation must check:
- referenced template exists and is accessible in tenant scope
- referenced template is active/usable for the request type’s tenant context
- every `templateField` in mappings exists in the template’s `formMap`
- every required template field is mapped
- every `sourceKey` exists in the request type’s known input universe:
  - payload fields from `payloadHandlerId`
  - computed/workflow fields from `RequestTypeModelerCapabilities`
- mapped value type is compatible with the template field type:
  - text-like template fields accept scalar string/number/boolean/date values after stringification
  - checkbox/radio/select fields must map only from compatible scalar or enum-like sources
- `ESIGN_REQUIRED` should require `template.esignable == true`
- `USER_TASK_GENERATED` should not require `esignable == true`
- duplicate `templateField` mappings are rejected
- duplicate association `name` values are rejected within a version
- if `taskDefinitionKey` is present, it must be a known workflow user task key for the selected process definition, or this must be explicitly deferred as unsupported and rejected for now

Validation output should be field-specific and configuration-specific, not generic. Errors should point to the association index/name and failing field.

### 4. Define the compatibility source of truth
Use the existing request-type capability system as the source of truth for what a request type can feed into a template.

Compatibility should be derived from:
- payload handler fields
- modeler input providers for computed/workflow fields

Do not duplicate that knowledge in template-linking code. Instead, introduce a reusable internal service that exposes a request type version’s available input descriptors in a machine-checkable form. The document-template association validator should depend on that service.

### 5. Keep generation/e-sign orchestration separate from association storage
The association layer should answer:
- which templates are linked to this request type version
- why each template is linked
- how each template field is populated

It should not itself perform generation or e-sign packaging.

Add an internal resolver service with methods shaped like:
- `resolveEsignTemplates(typeKey, version)`
- `resolveUserTaskGeneratedTemplates(typeKey, version, taskDefinitionKey)`
- `buildTemplateFieldMap(requestId/request payload + workflow context, association)`

That service becomes the bridge for future workflow/task code:
- e-sign step asks for `ESIGN_REQUIRED` templates
- task action step asks for `USER_TASK_GENERATED` templates for its `taskDefinitionKey`

## Public API / Interface Changes
Request-type create/change DTOs and commands should grow a versioned `documentTemplates` config section.

Request-type read/search responses should expose the active version’s document-template association summary, at least:
- `templateId`
- `role`
- `name`
- `taskDefinitionKey`
- `required`

Keep full mapping detail in the get-by-id/version response rather than the search summary if response size becomes noisy.

Document-template APIs do not need to change for v1, except possibly adding a lightweight reusable internal DTO/service for template metadata lookup during validation.

## Test Plan
Add coverage for:

- request type create/change succeeds with:
  - one valid `ESIGN_REQUIRED` association
  - one valid `USER_TASK_GENERATED` association
  - explicit mappings from payload fields
  - explicit mappings from workflow/computed fields where supported

- request type create/change fails when:
  - template does not exist
  - template tenant scope is incompatible
  - e-sign role references a non-esignable template
  - mapped template field does not exist
  - required template field is unmapped
  - source key does not exist in request-type capabilities
  - duplicate mapping targets the same template field
  - duplicate association names exist
  - `USER_TASK_GENERATED` is missing `taskDefinitionKey`
  - `taskDefinitionKey` is invalid for the process definition

- compatibility checks for field types:
  - text field from scalar payload source passes
  - checkbox/radio/select incompatible mapping fails

- resolver/orchestration unit tests:
  - e-sign associations filtered correctly
  - task-generated associations filtered correctly by `taskDefinitionKey`
  - field map builder produces the exact map expected by `DocumentTemplateApi.generate`

## Assumptions
- Associations should be versioned with request type versions, not global to the type.
- Explicit mapping is preferred over convention-based implicit binding in persisted config.
- `ESIGN_REQUIRED` templates must already be marked `esignable`.
- The existing request-type modeler capability universe is the authoritative source for what data is available when validating template compatibility.
- Workflow user-task targeting should use stable `taskDefinitionKey`, not task display name.
- If process-definition introspection for valid user task keys does not already exist, v1 should either add it or reject `USER_TASK_GENERATED` task binding until it does; do not silently allow unverifiable task keys.
