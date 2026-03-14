# Policy DMN Admin Configuration Specification (Phase 1)

## 1. Summary

- Problem: Policy behavior is hard to evolve safely without a governed admin interface for DMN construction and publishing.
- Business objective: Provide admin APIs and data contracts to define, validate, dry-run, version, and publish policy DMNs per request type.
- In scope:
  - Admin APIs for DMN policy configuration lifecycle.
  - Explicit allowed-input catalog model (payload fields + computable fields).
  - Explicit required-output contract for user-task generation.
  - Versioning, publish, dry-run, and in-flight version lock behavior.
  - Validation rules that prevent publishing unusable DMNs.
- Out of scope:
  - End-user request/task APIs.
  - Concrete enrichment implementations for specific domains.
  - UI implementation details (API-first only).

## 2. Confirmed Decisions

- Single BPMN process exists for all request types; this spec focuses only on policy admin configuration.
- New requests use latest published policy version for request type.
- In-flight requests remain locked to submission-time policy version.
- Policy service task is an external task.
- Enrichment is generic architecture and executed on every loop.
- Policy outputs must support user-task generation including order, parallel groups, assignments, and allowed actions.
- Task completion rule is strict ALL completion; policy-created tasks cannot be skipped.

## 3. Blocked Items (Must Resolve Before Build)

- None.

## 4. Feature Breakdown

### 4.1 Policy Definition Workspace (Draft)

- Description: Admin creates and updates draft policy versions per `requestType` with DMN artifacts and metadata.
- API impact: Create draft, update draft, validate draft, fetch draft, and IFTTT-style rule authoring.
- Data impact: Versioned persisted drafts with immutable published snapshots.
- Security impact: `policy.admin` role required for mutation.
- Observability impact: audit events for create/update/validate actions.

### 4.2 Input Catalog Configuration

- Description: Admin declares which fields are legal DMN inputs for each request type.
- API impact: Endpoints to register payload-derived inputs and computable-field inputs.
- Data impact: normalized input catalog entities tied to request type and policy version.
- Security impact: changes audited and publish-gated by validation.
- Observability impact: validation failure metrics by input rule.

### 4.3 Output Contract Configuration

- Description: Admin configures DMN outputs that map to required user-task generation schema.
- API impact: Endpoint to define output mapping and contract version.
- Data impact: output schema mapping, validation rules, and publish-time contract checks.
- Security impact: immutable output contract per published version.
- Observability impact: dry-run contract-violation metrics.

### 4.4 IFTTT Rule Authoring Model

- Description: Frontend builds rule sets using `IF <conditions> THEN <outputs>` payloads; backend compiles these into DMN decision tables.
- API impact: Rule-set upsert endpoint and compile diagnostics.
- Data impact: Store canonical rule JSON and generated DMN payload side-by-side for traceability.
- Security impact: rule mutations are admin-only and fully audited.
- Observability impact: compile success/failure metrics and rule-evaluation traces.

### 4.5 Dry-Run and Publish Governance

- Description: Draft must pass dry-run and validation before publish; publish activates version for new requests only.
- API impact: dry-run endpoint, publish endpoint, release notes endpoint.
- Data impact: publish metadata with actor/time/reason.
- Security impact: least-privilege separation for write vs publish optionally supported.
- Observability impact: publish events and dry-run usage telemetry.

### 4.6 Field Evaluation Architecture and Capability Discovery

- Description: Runtime resolves DMN inputs through a deterministic field-evaluation pipeline and exposes a discovery API so the admin UI can retrieve all possible inputs/outputs/operators.
- API impact: capability discovery endpoints and field-preview evaluation endpoint.
- Data impact: provider registry metadata, dependency graph metadata, and field provenance traces.
- Security impact: only admin roles can access discovery and preview endpoints; sensitive sample values are masked in responses/logs.
- Observability impact: per-provider latency/error metrics, dependency resolution metrics, cache hit metrics.

## 5. User Stories

### US-001: Define Draft Policy Version

- As a `Policy Administrator`
- I want to create a draft policy version for a request type
- So that I can evolve workflow policy safely before activation
- Priority: `P0`

### US-002: Configure Allowed Inputs

- As a `Policy Administrator`
- I want to configure allowed DMN inputs from payload and computable fields
- So that policy logic is constrained to approved data sources
- Priority: `P0`

### US-003: Configure Required Outputs

- As a `Policy Administrator`
- I want to define DMN outputs required for user task generation
- So that runtime task orchestration is deterministic and valid
- Priority: `P0`

### US-004: Dry-Run Policy

- As a `Policy Administrator`
- I want to execute dry-run evaluations against draft policy
- So that I can verify behavior before publish
- Priority: `P0`

### US-005: Publish Policy Version

- As a `Policy Administrator`
- I want to publish a validated draft
- So that new requests use the latest policy while in-flight requests remain stable
- Priority: `P0`

### US-006: Discover Allowed Inputs/Outputs in Admin UI

- As a `Policy Administrator`
- I want the UI to fetch all available input fields, operators, and output schema for a request type
- So that I can build rules without guessing technical field names
- Priority: `P0`

## 6. API Specification

### Endpoint: `GET /admin/policies/{requestType}/capabilities`

- Purpose: Return the full UI discovery contract for rule authoring: available input fields (payload + computable), supported operators by type, output contract schema, and validation limits.
- Auth: `policy.admin.read`
- Query params:
  - `version` (optional draft/published version; defaults to latest draft if exists else latest published)
- Response schema:
  - `requestType` (string)
  - `resolvedVersion` (integer)
  - `inputs` (object):
    - `payloadFields` (array): `{key, label, type, required, path, examples[]}`
    - `computableFields` (array): `{key, label, type, providerKey, dependsOn[], required, freshnessSlaSeconds}`
  - `operatorsByType` (object): map of `STRING|NUMBER|BOOLEAN|DATE|DATETIME|JSON` -> operator[]
  - `outputSchema` (object): required `then` output structure for task generation
  - `assignmentStrategies` (string[])
  - `validationLimits` (object): `{maxTreeDepth, maxNodesPerRule, maxRulesPerSet}`
  - `supportedHitPolicies` (string[])
- Errors: `403`, `404`

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "resolvedVersion": 7,
  "inputs": {
    "payloadFields": [
      {
        "key": "request.accountId",
        "label": "Account ID",
        "type": "STRING",
        "required": true,
        "path": "$.accountId",
        "examples": [
          "ACC-9012"
        ]
      },
      {
        "key": "request.requestedAmount",
        "label": "Requested Amount",
        "type": "NUMBER",
        "required": true,
        "path": "$.requestedAmount",
        "examples": [
          120000.0
        ]
      }
    ],
    "computableFields": [
      {
        "key": "computed.accountType",
        "label": "Account Type",
        "type": "STRING",
        "providerKey": "account-profile-provider",
        "dependsOn": [
          "request.accountId"
        ],
        "required": true,
        "freshnessSlaSeconds": 300
      }
    ]
  },
  "operatorsByType": {
    "STRING": ["EQ", "NEQ", "IN", "NOT_IN", "CONTAINS", "STARTS_WITH", "ENDS_WITH", "IS_NULL", "IS_NOT_NULL"],
    "NUMBER": ["EQ", "NEQ", "GT", "GTE", "LT", "LTE", "IN", "NOT_IN", "IS_NULL", "IS_NOT_NULL"],
    "BOOLEAN": ["EQ", "NEQ", "IS_NULL", "IS_NOT_NULL"]
  },
  "outputSchema": {
    "executionType": "HUMAN|COMPLETE",
    "then": {
      "taskPlanPatch": {
        "stages": "array",
        "completionPredicate": "ALL_TASKS_COMPLETED"
      },
      "assignmentHints": "object"
    }
  },
  "assignmentStrategies": ["STATIC", "POLICY_HINT", "BEST_USER_STUB"],
  "validationLimits": {
    "maxTreeDepth": 5,
    "maxNodesPerRule": 100,
    "maxRulesPerSet": 500
  },
  "supportedHitPolicies": ["FIRST", "COLLECT"]
}
```

### Endpoint: `POST /admin/policies/{requestType}/field-preview`

- Purpose: Evaluate selected computable fields for sample payload and return values + provenance (for admin debugging and rule-authoring confidence).
- Auth: `policy.admin`
- Request schema:
  - `version` (integer, required)
  - `requestPayload` (object, required)
  - `fields` (string[], required; subset of payload/computable keys)
- Response schema:
  - `resolvedFields` (object): key -> value
  - `provenance` (array): `{field, sourceType, providerKey, dependsOn, durationMs, status}`
  - `errors` (array): `{field, code, message}`
- Errors: `400`, `403`, `404`

Example request:
```json
{
  "version": 7,
  "requestPayload": {
    "accountId": "ACC-9012",
    "requestedAmount": 120000.0
  },
  "fields": [
    "request.requestedAmount",
    "computed.accountType",
    "computed.accountOwnerCount"
  ]
}
```

Example response (`200`):
```json
{
  "resolvedFields": {
    "request.requestedAmount": 120000.0,
    "computed.accountType": "BROKERAGE",
    "computed.accountOwnerCount": 2
  },
  "provenance": [
    {
      "field": "request.requestedAmount",
      "sourceType": "PAYLOAD",
      "providerKey": null,
      "dependsOn": [],
      "durationMs": 0,
      "status": "OK"
    },
    {
      "field": "computed.accountType",
      "sourceType": "COMPUTABLE",
      "providerKey": "account-profile-provider",
      "dependsOn": [
        "request.accountId"
      ],
      "durationMs": 22,
      "status": "OK"
    }
  ],
  "errors": []
}
```

### Endpoint: `POST /admin/policies/{requestType}/versions`

- Purpose: Create draft version container for policy configuration.
- Auth: `policy.admin`
- Request schema:
  - `displayName` (string, required)
  - `description` (string, optional)
  - `basedOnVersion` (integer, optional)
- Response schema:
  - `requestType` (string)
  - `version` (integer)
  - `state` (`DRAFT`)
  - `createdBy` (string)
  - `createdAt` (timestamp)
- Errors: `400`, `403`, `404`.
- Idempotency: not required.

Example request:
```json
{
  "displayName": "Wire Transfer Policy",
  "description": "Phase 1 human-task policy for wire transfer",
  "basedOnVersion": 6
}
```

Example response (`201`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "state": "DRAFT",
  "createdBy": "policy.admin1",
  "createdAt": "2026-03-14T19:02:11Z"
}
```

### Endpoint: `PUT /admin/policies/{requestType}/versions/{version}/input-catalog`

- Purpose: Replace allowed DMN input catalog for the draft version.
- Auth: `policy.admin`
- Request schema:
  - `contractVersion` (string, required)
  - `payloadInputs` (array, required):
    - `key` (string, required)  (example: `request.accountId`)
    - `type` (`STRING|NUMBER|BOOLEAN|DATE|DATETIME|JSON`, required)
    - `required` (boolean, required)
    - `description` (string, optional)
  - `computableInputs` (array, required):
    - `key` (string, required)  (example: `computed.accountType`)
    - `type` (`STRING|NUMBER|BOOLEAN|DATE|DATETIME|JSON`, required)
    - `providerKey` (string, required)  (generic enrichment provider id)
    - `dependsOn` (string[], required)  (keys from payload or computable catalog)
    - `cacheTtlSeconds` (integer, optional)
    - `required` (boolean, required)
- Response schema:
  - `requestType`, `version`, `inputCatalogHash`, `updatedAt`, `updatedBy`
- Errors:
  - `400` invalid schema, duplicate key, cyclic dependency
  - `403` unauthorized
  - `409` version not mutable (already published)

Example request:
```json
{
  "contractVersion": "1.0.0",
  "payloadInputs": [
    {
      "key": "request.accountId",
      "type": "STRING",
      "required": true,
      "description": "Primary account identifier from request payload"
    },
    {
      "key": "request.requestedAmount",
      "type": "NUMBER",
      "required": true
    },
    {
      "key": "request.requestedByUserId",
      "type": "STRING",
      "required": true
    }
  ],
  "computableInputs": [
    {
      "key": "computed.accountType",
      "type": "STRING",
      "providerKey": "account-profile-provider",
      "dependsOn": [
        "request.accountId"
      ],
      "cacheTtlSeconds": 300,
      "required": true
    },
    {
      "key": "computed.accountOwnerCount",
      "type": "NUMBER",
      "providerKey": "account-owners-provider",
      "dependsOn": [
        "request.accountId"
      ],
      "required": false
    }
  ]
}
```

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "inputCatalogHash": "sha256:f2b9bcd4470ce1d0...",
  "updatedAt": "2026-03-14T19:03:54Z",
  "updatedBy": "policy.admin1"
}
```

Example error (`400`, cyclic dependency):
```json
{
  "code": "INPUT_CATALOG_INVALID",
  "message": "Cyclic dependency detected",
  "details": [
    {
      "path": "computableInputs[2].dependsOn",
      "reason": "Cycle: computed.a -> computed.b -> computed.a"
    }
  ],
  "traceId": "f1324b7b8a6148cd"
}
```

### Endpoint: `PUT /admin/policies/{requestType}/versions/{version}/output-contract`

- Purpose: Define required DMN output schema and mapping for user-task generation.
- Auth: `policy.admin`
- Request schema:
  - `contractVersion` (string, required)
  - `outputSchema` (object, required):
    - `executionType` (`HUMAN|COMPLETE`, required)
    - `stages` (array, required when `executionType=HUMAN`):
      - `stageOrder` (integer, required, 1..n)
      - `completionRule` (`ALL`, required)
      - `tasks` (array, required):
        - `taskKey` (string, required)
        - `taskName` (string, required)
        - `parallelGroupId` (string, optional)
        - `candidateUsers` (string[], optional)
        - `candidateGroups` (string[], optional)
        - `assignmentStrategy` (`STATIC|POLICY_HINT|BEST_USER_STUB`, required)
        - `allowedActions` (string[], required, min 1)
        - `actionOutcomes` (object, optional)  (maps action -> next decision hints)
    - `completionPredicate` (string, required)  (`ALL_TASKS_COMPLETED` for phase 1)
- Response schema:
  - `requestType`, `version`, `outputContractHash`, `updatedAt`, `updatedBy`
- Errors:
  - `400` invalid output contract
  - `409` version not mutable
  - `403` unauthorized

Example request:
```json
{
  "contractVersion": "1.0.0",
  "outputSchema": {
    "executionType": "HUMAN",
    "stages": [
      {
        "stageOrder": 1,
        "completionRule": "ALL",
        "tasks": [
          {
            "taskKey": "review_request",
            "taskName": "Review Request",
            "assignmentStrategy": "BEST_USER_STUB",
            "candidateGroups": [
              "Ops Reviewers"
            ],
            "allowedActions": [
              "APPROVE",
              "REJECT",
              "REQUEST_INFO"
            ]
          }
        ]
      },
      {
        "stageOrder": 2,
        "completionRule": "ALL",
        "tasks": [
          {
            "taskKey": "fraud_check",
            "taskName": "Fraud Check",
            "parallelGroupId": "grp-risk",
            "assignmentStrategy": "STATIC",
            "candidateGroups": [
              "Risk Team"
            ],
            "allowedActions": [
              "CLEAR",
              "FLAG"
            ]
          },
          {
            "taskKey": "compliance_check",
            "taskName": "Compliance Check",
            "parallelGroupId": "grp-risk",
            "assignmentStrategy": "STATIC",
            "candidateGroups": [
              "Compliance Team"
            ],
            "allowedActions": [
              "CLEAR",
              "HOLD"
            ]
          }
        ]
      }
    ],
    "completionPredicate": "ALL_TASKS_COMPLETED"
  }
}
```

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "outputContractHash": "sha256:80f94af845cb65f4...",
  "updatedAt": "2026-03-14T19:05:29Z",
  "updatedBy": "policy.admin1"
}
```

Example error (`400`, invalid contract):
```json
{
  "code": "OUTPUT_CONTRACT_INVALID",
  "message": "Task definition missing required field",
  "details": [
    {
      "path": "outputSchema.stages[0].tasks[0].allowedActions",
      "reason": "must contain at least 1 item"
    }
  ],
  "traceId": "fe260fb906bf4d7c"
}
```

### Endpoint: `PUT /admin/policies/{requestType}/versions/{version}/dmn-bundle`

- Purpose: Attach DMN definitions and mapping metadata used by policy evaluator.
- Auth: `policy.admin`
- Request schema:
  - `dmnFiles` (array, required):
    - `decisionKey` (string)
    - `xml` (string, required)
    - `checksum` (string, required)
  - `inputBinding` (object, required)  (maps catalog keys -> DMN inputs)
  - `outputBinding` (object, required) (maps DMN outputs -> output contract fields)
- Response schema:
  - `requestType`, `version`, `dmnBundleHash`, `updatedAt`
- Errors: `400`, `403`, `409`.

Example request:
```json
{
  "dmnFiles": [
    {
      "decisionKey": "policy_execution_type",
      "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions>...</definitions>",
      "checksum": "sha256:1a46..."
    },
    {
      "decisionKey": "policy_task_plan",
      "xml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions>...</definitions>",
      "checksum": "sha256:3cb8..."
    }
  ],
  "inputBinding": {
    "request.accountId": "dmn.accountId",
    "computed.accountType": "dmn.accountType",
    "request.requestedAmount": "dmn.requestedAmount"
  },
  "outputBinding": {
    "dmn.executionType": "outputSchema.executionType",
    "dmn.stages": "outputSchema.stages",
    "dmn.completionPredicate": "outputSchema.completionPredicate"
  }
}
```

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "dmnBundleHash": "sha256:95f952ec5500cb86...",
  "updatedAt": "2026-03-14T19:07:10Z"
}
```

### Endpoint: `PUT /admin/policies/{requestType}/versions/{version}/rule-set`

- Purpose: Upsert IFTTT-style rule definitions that the backend compiles into DMN payloads.
- Auth: `policy.admin`
- Request schema:
  - `ruleSetVersion` (string, required)
  - `hitPolicy` (`FIRST|COLLECT`, required)
  - `defaultOutcome` (object, required)  (applied when no rule matches)
  - `rules` (array, required):
    - `ruleId` (string, required)
    - `priority` (integer, required, lower executes first)
    - `enabled` (boolean, required)
    - `if` (object, required, recursive condition tree):
      - `nodeType` (`GROUP|CONDITION`, required)
      - `groupOp` (`AND|OR`, required when `nodeType=GROUP`)
      - `children` (array of nodes, required when `nodeType=GROUP`, min 2)
      - `field` (string, required when `nodeType=CONDITION`, must exist in input catalog)
      - `operator` (`EQ|NEQ|GT|GTE|LT|LTE|IN|NOT_IN|CONTAINS|STARTS_WITH|ENDS_WITH|IS_NULL|IS_NOT_NULL`, required when `nodeType=CONDITION`)
      - `valueType` (`STRING|NUMBER|BOOLEAN|DATE|DATETIME|JSON`, required when `nodeType=CONDITION`)
      - `value` (any, optional for null operators)
    - `then` (object, required):
      - `executionType` (`HUMAN|COMPLETE`, required)
      - `taskPlanPatch` (object, optional, must conform to output contract)
      - `assignmentHints` (object, optional)
- Response schema:
  - `requestType`, `version`, `ruleSetHash`, `compiledDmnHash`, `compileStatus`, `updatedAt`
- Errors:
  - `400` invalid rules or unknown fields/operators or invalid condition-tree structure
  - `403` unauthorized
  - `409` version not mutable

Example request:
```json
{
  "ruleSetVersion": "1.0.0",
  "hitPolicy": "FIRST",
  "defaultOutcome": {
    "executionType": "COMPLETE"
  },
  "rules": [
    {
      "ruleId": "r-high-value-brokerage",
      "priority": 10,
      "enabled": true,
      "if": {
        "nodeType": "GROUP",
        "groupOp": "OR",
        "children": [
          {
            "nodeType": "GROUP",
            "groupOp": "AND",
            "children": [
              {
                "nodeType": "CONDITION",
                "field": "request.requestedAmount",
                "operator": "GTE",
                "valueType": "NUMBER",
                "value": 100000
              },
              {
                "nodeType": "CONDITION",
                "field": "computed.accountType",
                "operator": "EQ",
                "valueType": "STRING",
                "value": "BROKERAGE"
              }
            ]
          },
          {
            "nodeType": "GROUP",
            "groupOp": "AND",
            "children": [
              {
                "nodeType": "CONDITION",
                "field": "request.destinationCountry",
                "operator": "EQ",
                "valueType": "STRING",
                "value": "US"
              },
              {
                "nodeType": "GROUP",
                "groupOp": "OR",
                "children": [
                  {
                    "nodeType": "CONDITION",
                    "field": "computed.accountOwnerCount",
                    "operator": "GT",
                    "valueType": "NUMBER",
                    "value": 2
                  },
                  {
                    "nodeType": "CONDITION",
                    "field": "request.isUrgent",
                    "operator": "EQ",
                    "valueType": "BOOLEAN",
                    "value": true
                  }
                ]
              }
            ]
          }
        ]
      },
      "then": {
        "executionType": "HUMAN",
        "taskPlanPatch": {
          "stages": [
            {
              "stageOrder": 1,
              "completionRule": "ALL",
              "tasks": [
                {
                  "taskKey": "review_request",
                  "taskName": "Review Request",
                  "assignmentStrategy": "BEST_USER_STUB",
                  "candidateGroups": [
                    "Ops Reviewers"
                  ],
                  "allowedActions": [
                    "APPROVE",
                    "REJECT"
                  ]
                }
              ]
            }
          ],
          "completionPredicate": "ALL_TASKS_COMPLETED"
        },
        "assignmentHints": {
          "strategyHint": "LOAD_BALANCED"
        }
      }
    }
  ]
}
```

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "ruleSetHash": "sha256:14db62b36f41311a...",
  "compiledDmnHash": "sha256:2407d445d45fb8d1...",
  "compileStatus": "SUCCESS",
  "updatedAt": "2026-03-14T19:08:12Z"
}
```

Example error (`400`):
```json
{
  "code": "RULESET_INVALID",
  "message": "Unknown input field in rule condition",
  "details": [
    {
      "path": "rules[0].if.children[0].children[1].field",
      "reason": "Field 'computed.accountTier' is not in input catalog"
    }
  ],
  "traceId": "211dcb7195d74001"
}
```

### Endpoint: `POST /admin/policies/{requestType}/versions/{version}/validate`

- Purpose: Validate structural consistency across input catalog, DMN bindings, and output contract.
- Auth: `policy.admin`
- Request schema: empty.
- Response schema:
  - `status` (`PASS|FAIL`)
  - `errors` (array): `{code, message, path}`
  - `warnings` (array): `{code, message, path}`
  - `compiledDmnPayload` (object, optional) (when rule-set is configured)
- Errors: `403`, `404`, `409`.

Example response (`200`, pass):
```json
{
  "status": "PASS",
  "errors": [],
  "compiledDmnPayload": {
    "definitions": [
      {
        "decisionKey": "policy_execution_type",
        "hitPolicy": "FIRST",
        "inputs": [
          "request.requestedAmount",
          "computed.accountType"
        ],
        "outputs": [
          "executionType"
        ],
        "rules": [
          {
            "ruleId": "r-high-value-brokerage",
            "priority": 10,
            "when": {
              "nodeType": "GROUP",
              "groupOp": "OR",
              "children": [
                {
                  "nodeType": "GROUP",
                  "groupOp": "AND",
                  "children": [
                    {
                      "nodeType": "CONDITION",
                      "input": "request.requestedAmount",
                      "op": "GTE",
                      "value": 100000
                    },
                    {
                      "nodeType": "CONDITION",
                      "input": "computed.accountType",
                      "op": "EQ",
                      "value": "BROKERAGE"
                    }
                  ]
                },
                {
                  "nodeType": "CONDITION",
                  "input": "request.isUrgent",
                  "op": "EQ",
                  "value": true
                }
              ]
            },
            "then": {
              "executionType": "HUMAN"
            }
          }
        ],
        "default": {
          "executionType": "COMPLETE"
        }
      }
    ]
  },
  "warnings": [
    {
      "code": "COMPUTED_INPUT_OPTIONAL",
      "message": "computed.accountOwnerCount is optional and may be null at runtime",
      "path": "computableInputs[1]"
    }
  ]
}
```

Example response (`200`, fail):
```json
{
  "status": "FAIL",
  "errors": [
    {
      "code": "UNBOUND_DMN_OUTPUT",
      "message": "DMN output 'taskAssignments' has no output binding",
      "path": "outputBinding"
    }
  ],
  "warnings": []
}
```

### Endpoint: `POST /admin/policies/{requestType}/versions/{version}/dry-run`

- Purpose: Execute policy evaluation with sample payload and optional computable-field overrides, without mutating runtime state.
- Auth: `policy.admin`
- Request schema:
  - `requestPayload` (object, required)
  - `computedOverrides` (object, optional)
  - `loopIteration` (integer, optional, default `1`)
- Response schema:
  - `resolvedInputs` (object)
  - `executionType` (`HUMAN|COMPLETE`)
  - `taskPlan` (object per output contract)
  - `validationMessages` (array)
  - `trace` (object: decision keys, hit rules, timings)
- Errors:
  - `400` bad sample input
  - `403` unauthorized
  - `404` version not found

Example request:
```json
{
  "requestPayload": {
    "accountId": "ACC-9012",
    "requestedAmount": 120000.0,
    "requestedByUserId": "u-1029",
    "destinationCountry": "US"
  },
  "computedOverrides": {
    "computed.accountType": "BROKERAGE",
    "computed.accountOwnerCount": 2
  },
  "loopIteration": 1
}
```

Example response (`200`):
```json
{
  "resolvedInputs": {
    "request.accountId": "ACC-9012",
    "request.requestedAmount": 120000.0,
    "request.requestedByUserId": "u-1029",
    "computed.accountType": "BROKERAGE",
    "computed.accountOwnerCount": 2
  },
  "executionType": "HUMAN",
  "taskPlan": {
    "stages": [
      {
        "stageOrder": 1,
        "completionRule": "ALL",
        "tasks": [
          {
            "taskKey": "review_request",
            "taskName": "Review Request",
            "candidateUsers": [],
            "candidateGroups": [
              "Ops Reviewers"
            ],
            "assignmentStrategy": "BEST_USER_STUB",
            "allowedActions": [
              "APPROVE",
              "REJECT",
              "REQUEST_INFO"
            ]
          }
        ]
      }
    ],
    "completionPredicate": "ALL_TASKS_COMPLETED"
  },
  "validationMessages": [],
  "trace": {
    "decisions": [
      {
        "decisionKey": "policy_execution_type",
        "hitRules": [
          "r3"
        ],
        "durationMs": 9
      },
      {
        "decisionKey": "policy_task_plan",
        "hitRules": [
          "r11"
        ],
        "durationMs": 14
      }
    ],
    "totalDurationMs": 31
  }
}
```

### DMN Payload Shape (Canonical)

This is the canonical backend payload generated from IFTTT rules and/or accepted in raw DMN mode.

```json
{
  "definitions": [
    {
      "decisionKey": "string",
      "hitPolicy": "FIRST|COLLECT",
      "inputs": [
        "request.fieldPath",
        "computed.fieldPath"
      ],
      "outputs": [
        "executionType",
        "taskPlan",
        "assignmentHints"
      ],
      "rules": [
        {
          "ruleId": "string",
          "priority": 10,
          "enabled": true,
          "when": {
            "nodeType": "GROUP|CONDITION",
            "groupOp": "AND|OR",
            "children": [],
            "input": "request.requestedAmount",
            "op": "GTE",
            "valueType": "NUMBER",
            "value": 100000
          },
          "then": {
            "executionType": "HUMAN",
            "taskPlanPatch": {},
            "assignmentHints": {}
          }
        }
      ],
      "default": {
        "executionType": "COMPLETE"
      }
    }
  ],
  "metadata": {
    "requestType": "WIRE_TRANSFER",
    "policyVersion": 7,
    "generatedFrom": "IFTTT_RULE_SET|RAW_DMN_UPLOAD"
  }
}
```

Rules for frontend authoring:
- Every condition node `if/when` field must be declared in the version input catalog.
- `then.taskPlanPatch` must validate against the configured output contract.
- `FIRST` hit policy requires deterministic priority ordering.
- A `defaultOutcome` is mandatory and maps to DMN `default`.
- Unknown operators or type mismatches fail validation and block publish.
- Precedence is defined only by tree nesting; there is no implicit operator precedence.
- Validation limits for phase 1: max depth `5`, max total nodes `100` per rule, min `2` children for `GROUP`.

### Endpoint: `POST /admin/policies/{requestType}/versions/{version}/publish`

- Purpose: Publish draft version as latest active version for new requests.
- Auth: `policy.admin.publish`
- Request schema:
  - `changeReason` (string, required)
  - `publishedBy` (string, required)
- Response schema:
  - `requestType`
  - `version`
  - `state` (`PUBLISHED`)
  - `publishedAt`
  - `isLatest` (`true`)
- Errors:
  - `409` validate not passing or version not draft
  - `403` unauthorized
  - `404` version not found

Example request:
```json
{
  "changeReason": "Adjust high-value approvals and parallel risk checks",
  "publishedBy": "policy.publisher1"
}
```

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "state": "PUBLISHED",
  "publishedAt": "2026-03-14T19:10:42Z",
  "isLatest": true
}
```

Example error (`409`):
```json
{
  "code": "PUBLISH_PRECONDITION_FAILED",
  "message": "Policy version cannot be published",
  "details": [
    {
      "path": "validation.status",
      "reason": "Latest validation status is FAIL"
    }
  ],
  "traceId": "91d9d1ac8b4a49b5"
}
```

### Endpoint: `GET /admin/policies/{requestType}/versions`

- Purpose: List versions and lifecycle states.
- Auth: `policy.admin.read`
- Query params:
  - `state` (optional: `DRAFT|PUBLISHED|ARCHIVED`)
  - `page`, `size`
- Response schema:
  - list of `{version, state, createdAt, createdBy, publishedAt, publishedBy, isLatest}`

Example response (`200`):
```json
{
  "items": [
    {
      "version": 7,
      "state": "PUBLISHED",
      "createdAt": "2026-03-14T19:02:11Z",
      "createdBy": "policy.admin1",
      "publishedAt": "2026-03-14T19:10:42Z",
      "publishedBy": "policy.publisher1",
      "isLatest": true
    },
    {
      "version": 6,
      "state": "ARCHIVED",
      "createdAt": "2026-03-08T15:01:17Z",
      "createdBy": "policy.admin1",
      "publishedAt": "2026-03-08T15:10:02Z",
      "publishedBy": "policy.publisher1",
      "isLatest": false
    }
  ],
  "page": 0,
  "size": 20,
  "total": 2
}
```

### Endpoint: `GET /admin/policies/{requestType}/versions/{version}`

- Purpose: Get full configuration details for a specific version.
- Auth: `policy.admin.read`
- Response schema:
  - version metadata + input catalog + output contract + DMN binding summary

Example response (`200`):
```json
{
  "requestType": "WIRE_TRANSFER",
  "version": 7,
  "state": "PUBLISHED",
  "isLatest": true,
  "metadata": {
    "displayName": "Wire Transfer Policy",
    "description": "Phase 1 human-task policy for wire transfer",
    "createdBy": "policy.admin1",
    "createdAt": "2026-03-14T19:02:11Z",
    "publishedBy": "policy.publisher1",
    "publishedAt": "2026-03-14T19:10:42Z"
  },
  "inputCatalog": {
    "contractVersion": "1.0.0",
    "payloadInputs": [
      {
        "key": "request.accountId",
        "type": "STRING",
        "required": true
      }
    ],
    "computableInputs": [
      {
        "key": "computed.accountType",
        "type": "STRING",
        "providerKey": "account-profile-provider",
        "dependsOn": [
          "request.accountId"
        ],
        "required": true
      }
    ]
  },
  "outputContract": {
    "contractVersion": "1.0.0",
    "outputSchema": {
      "executionType": "HUMAN",
      "completionPredicate": "ALL_TASKS_COMPLETED"
    }
  },
  "dmnBundle": {
    "decisionKeys": [
      "policy_execution_type",
      "policy_task_plan"
    ],
    "dmnBundleHash": "sha256:95f952ec5500cb86..."
  }
}
```

## 7. Data Model and Persistence

- Tables/entities affected:
  - `policy_version`
    - `request_type`, `version`, `state`, `is_latest`, `created_by`, `created_at`, `published_by`, `published_at`, `change_reason`
  - `policy_input_catalog`
    - `policy_version_id`, `key`, `source_type` (`PAYLOAD|COMPUTABLE`), `data_type`, `required`, `provider_key`, `depends_on_json`, `cache_ttl_seconds`
  - `policy_output_contract`
    - `policy_version_id`, `contract_version`, `schema_json`, `completion_predicate`
  - `policy_dmn_bundle`
    - `policy_version_id`, `bundle_json`, `checksum`, `input_binding_json`, `output_binding_json`
  - `policy_rule_set`
    - `policy_version_id`, `rule_set_json`, `hit_policy`, `rule_set_hash`, `compiled_dmn_hash`, `compile_status`, `updated_at`, `updated_by`
  - `policy_validation_result`
    - `policy_version_id`, `status`, `errors_json`, `warnings_json`, `validated_at`, `validated_by`
  - `policy_dry_run_result` (retained by policy)
    - `policy_version_id`, `input_hash`, `result_json`, `created_at`, `created_by`
  - `policy_audit_event`
    - `policy_version_id`, `event_type`, `actor`, `event_json`, `created_at`
  - `field_provider_registry`
    - `provider_key`, `display_name`, `status`, `input_schema_json`, `output_schema_json`, `timeout_ms`, `cacheable`, `created_at`, `updated_at`
  - `field_catalog_snapshot`
    - `policy_version_id`, `payload_fields_json`, `computable_fields_json`, `operators_by_type_json`, `validation_limits_json`
  - `field_evaluation_trace`
    - `request_type`, `policy_version`, `trace_id`, `resolved_fields_json`, `provenance_json`, `created_at`
- New columns/constraints/indexes:
  - Unique `(request_type, version)`.
  - Unique latest guard: one `is_latest=true` per `request_type`.
  - Indexes on `policy_version(request_type, state, is_latest)` and `policy_input_catalog(policy_version_id, key)`.
  - Index on `field_provider_registry(provider_key, status)`.
  - Index on `field_evaluation_trace(request_type, policy_version, created_at)`.
- Transaction boundaries:
  - Publish flips `is_latest` atomically (old latest false, new latest true).
  - Validate and dry-run do not mutate published state.
- Concurrency strategy:
  - Optimistic locking on mutable draft resources.
  - Publish guarded by version state and lock token.

## 8. Camunda 7 Workflow Specification

- Process key/name: `UniversalRequestProcess`.
- Trigger interaction with admin config:
  - At submission time, runtime resolves latest published policy version and stores `policyVersion` variable.
  - Every policy loop uses the locked version for that process instance.
- Variables used from admin config:
  - `policyVersion`, `executionType`, `nextTaskPlan`, `allowedActionsByTask`.
- Service tasks and retry strategy:
  - External task topic: `policy-evaluator-v1`.
  - Worker loads versioned config and executes DMN with runtime resolved inputs.
  - Field evaluation pipeline before DMN execution:
    - Build dependency DAG from requested DMN inputs.
    - Resolve payload fields directly.
    - Resolve computable fields by topological order through provider adapters.
    - Apply cache by `providerKey + inputHash` when TTL allows.
    - Store field provenance for audit/debug (`field`, `provider`, `duration`, `status`).
    - Fail fast on required-field resolution failure; route to App Support incident path.
- Incidents/escalations:
  - Invalid runtime evaluation against published config routes to Application Support path.
- Compensation/cancellation behavior:
  - Not defined in this admin-focused phase.

## 9. Non-Functional Requirements

- Performance targets:
  - Admin validate p95 <= 1s for typical DMN bundle size.
  - Admin dry-run p95 <= 2s excluding external enrichment latency.
  - Publish p95 <= 500ms.
  - Field preview p95 <= 800ms with warm cache for <=10 fields.
- Reliability/SLA expectations:
  - Publish operation is atomic and reversible only by publishing another version.
  - No partial config visibility to runtime.
- Security controls:
  - Separate permissions for read, edit, and publish.
  - Full audit trail for all mutations and publish events.
  - Sensitive input samples in dry-run masked/redacted in logs.
- Observability (logs/metrics/traces):
  - Metrics: validate pass/fail, dry-run count/failures, publish count, publish rollback attempts.
  - Metrics: field evaluation latency/error per `providerKey`, dependency DAG build failures, cache hit ratio.
  - Structured logs with `requestType`, `version`, `actor`, `traceId`.

## 10. Acceptance Tests (Integration)

### AT-001: Create Draft Version

- Covers user story: `US-001`
- Preconditions:
  - Request type exists.
- Test steps:
  - Call `POST /admin/policies/{requestType}/versions`.
- Expected API result:
  - `201` with `state=DRAFT` and incremented version.
- Expected DB state:
  - `policy_version` row inserted.
- Expected Camunda state:
  - No process mutation.

### AT-002: Configure Allowed Inputs with Dependency Validation

- Covers user story: `US-002`
- Preconditions:
  - Draft exists.
- Test steps:
  - PUT input catalog with computable field depending on unknown key.
  - PUT corrected catalog.
- Expected API result:
  - First call `400`, second call success.
- Expected DB state:
  - Only corrected catalog persisted.
- Expected Camunda state:
  - No process mutation.

### AT-003: Reject Output Contract Missing Required Task Fields

- Covers user story: `US-003`
- Preconditions:
  - Draft exists.
- Test steps:
  - PUT output contract missing `allowedActions`.
- Expected API result:
  - `400` contract error.
- Expected DB state:
  - No invalid output contract persisted.
- Expected Camunda state:
  - No process mutation.

### AT-004: Dry-Run Produces Human Task Plan

- Covers user story: `US-004`
- Preconditions:
  - Draft has valid input catalog, output contract, and DMN bundle.
- Test steps:
  - Call dry-run with payload sample.
- Expected API result:
  - `200`, `executionType=HUMAN`, includes stages/tasks/assignments/actions.
- Expected DB state:
  - Optional dry-run artifact recorded.
- Expected Camunda state:
  - No process mutation.

### AT-004B: IFTTT Rule Set Compiles to Valid DMN Payload

- Covers user story: `US-003`, `US-004`
- Preconditions:
  - Draft has valid input catalog and output contract.
- Test steps:
  - PUT `rule-set` with one valid rule and default outcome.
  - Call validate endpoint.
- Expected API result:
  - Rule-set upsert returns `compileStatus=SUCCESS`.
  - Validate returns `status=PASS` and non-empty `compiledDmnPayload`.
- Expected DB state:
  - `policy_rule_set` row persisted with `compiled_dmn_hash`.
- Expected Camunda state:
  - No process mutation.

### AT-005: Publish Requires Passing Validation

- Covers user story: `US-005`
- Preconditions:
  - Draft exists and validation status is FAIL.
- Test steps:
  - Attempt publish.
  - Fix config, validate PASS, publish again.
- Expected API result:
  - First publish `409`, second publish success.
- Expected DB state:
  - Exactly one latest version for request type after success.
- Expected Camunda state:
  - New submissions resolve published version.

### AT-006: In-Flight Version Lock After New Publish

- Covers user story: `US-005`
- Preconditions:
  - Runtime request instance already locked to policy version N.
  - Version N+1 is published.
- Test steps:
  - Trigger policy loop for existing in-flight request.
- Expected API result:
  - Runtime evaluation succeeds.
- Expected DB state:
  - In-flight request remains locked to N.
- Expected Camunda state:
  - External task evaluation uses version N.

### AT-007: Capabilities Endpoint Returns UI Authoring Contract

- Covers user story: `US-006`
- Preconditions:
  - Version has input catalog and output contract configured.
- Test steps:
  - GET `/admin/policies/WIRE_TRANSFER/capabilities?version=7`.
- Expected API result:
  - `200` with payload fields, computable fields, operators by type, output schema, and validation limits.
- Expected DB state:
  - No mutation.
- Expected Camunda state:
  - No process mutation.

### AT-008: Field Preview Resolves Computable Inputs with Provenance

- Covers user story: `US-006`
- Preconditions:
  - Field providers registered and reachable.
- Test steps:
  - POST field-preview for a mix of payload and computable fields.
- Expected API result:
  - `200` with resolved values and provenance rows per field.
- Expected DB state:
  - Optional `field_evaluation_trace` row persisted for diagnostics.
- Expected Camunda state:
  - No process mutation.

## 11. Edge Cases and Failure Scenarios

- Case: Cyclic computable input dependencies.
- Expected behavior: validation fails; publish blocked.

- Case: DMN output includes task with no assignee source.
- Expected behavior: validation fails unless assignment strategy is `BEST_USER_STUB` with fallback candidate group.

- Case: Publish race between two admins.
- Expected behavior: optimistic lock conflict; one publish succeeds, one fails with `409`.

- Case: Dry-run payload misses required payload input.
- Expected behavior: `400` with field-level validation errors.

## 12. Adjacent Feature Recommendations

- Recommendation: Add maker-checker approval workflow before publish.
- Reason: change governance for sensitive request types.
- Include now? `no`

- Recommendation: Add policy diff endpoint between versions.
- Reason: easier review of DMN/input/output changes.
- Include now? `yes`

- Recommendation: Add synthetic test suite endpoint to run multiple dry-run fixtures.
- Reason: stronger pre-publish confidence.
- Include now? `yes`
