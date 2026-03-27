# Reference: DocuSign Configuration

## Application properties

The app binds the following `docusign.*` properties.

| Property | Env var | Required | Purpose |
|---|---|---|---|
| `docusign.base-path` | `DOCUSIGN_BASE_PATH` | Yes | DocuSign REST API base path. Use `https://demo.docusign.net/restapi` for developer accounts. |
| `docusign.account-id` | `DOCUSIGN_ACCOUNT_ID` | Yes | Target DocuSign account for envelope operations. |
| `docusign.access-token` | `DOCUSIGN_ACCESS_TOKEN` | Yes | Bearer token used by the Java SDK. |
| `docusign.webhook-hmac-secret` | `DOCUSIGN_WEBHOOK_HMAC_SECRET` | Yes | Shared HMAC secret used to verify DocuSign Connect payloads. |
| `docusign.return-url` | `DOCUSIGN_RETURN_URL` | Yes for embedded signing | Return URL used when creating embedded recipient views. |
| `docusign.host-email` | `DOCUSIGN_HOST_EMAIL` | Optional | Host signer email for in-person signing flows. |
| `docusign.host-name` | `DOCUSIGN_HOST_NAME` | Optional | Host signer display name for in-person signing flows. |

## Required DocuSign account setup

The app assumes the following are configured in DocuSign:

- API access is enabled for the account
- the bearer token in `DOCUSIGN_ACCESS_TOKEN` is valid for the configured account
- Connect is enabled for webhook delivery
- HMAC signing is enabled for the Connect configuration

## Webhook endpoint

DocuSign should post Connect events to:

```text
POST /api/esign/envelopes/webhooks/docusign
```

This route is intentionally anonymous at the Spring Security layer so DocuSign can reach it without app credentials. Authenticity is enforced by verifying the HMAC signature from `X-DocuSign-Signature-1`.

## Envelope behavior supported by this app

- Remote signing
  - signer authentication can require a passcode or SMS
  - reminder cadence is configured on the envelope and managed by DocuSign
- In-person signing
  - the app creates recipient views on demand
  - embedded signing URLs are not stored in the database
- Webhook updates
  - envelope status is updated from DocuSign events
  - signer status and timestamps are updated from recipient-level events
- Completed artifacts
  - the app stores the completed signed PDF
  - the app stores the signing certificate

## Minimal local example

```bash
export DOCUSIGN_BASE_PATH="https://demo.docusign.net/restapi"
export DOCUSIGN_ACCOUNT_ID="YOUR_ACCOUNT_ID"
export DOCUSIGN_ACCESS_TOKEN="YOUR_ACCESS_TOKEN"
export DOCUSIGN_WEBHOOK_HMAC_SECRET="replace-with-a-long-random-secret"
export DOCUSIGN_RETURN_URL="https://app.example.com/esign/return"
```
