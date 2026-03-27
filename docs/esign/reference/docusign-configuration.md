# Reference: DocuSign Configuration

This page lists the application and DocuSign settings required for the e-sign module.

## Application environment variables

`DOCUSIGN_BASE_PATH`

- Default: `https://demo.docusign.net/restapi`
- DocuSign REST API base URL

`DOCUSIGN_ACCOUNT_ID`

- Required
- Target DocuSign account ID used by the SDK

`DOCUSIGN_ACCESS_TOKEN`

- Required
- Bearer token used by the SDK for envelope and document operations

`DOCUSIGN_RETURN_URL`

- Required for embedded signing
- Return URL used when creating recipient views

`DOCUSIGN_HOST_EMAIL`

- Optional
- Reserved for host identity configuration

`DOCUSIGN_HOST_NAME`

- Optional
- Reserved for host identity configuration

`DOCUSIGN_WEBHOOK_SHARED_SECRET`

- Required for webhooks
- Shared secret used to verify the `X-DocuSign-Signature-1` HMAC header

## App endpoints

`POST /api/esign/envelopes`

- Creates envelopes from uploaded PDFs and signer details

`POST /api/esign/envelopes/{id}/signers/{roleKey}/embedded-view`

- Returns a fresh recipient view URL for in-person signing

`POST /api/esign/envelopes/webhooks/docusign`

- Receives DocuSign Connect webhook events
- Publicly reachable
- Does not require user authentication
- Requires valid HMAC signature verification

## DocuSign Connect settings

Configure Connect to send webhook events to:

```text
https://your-public-host/api/esign/envelopes/webhooks/docusign
```

Recommended settings:

- JSON payloads enabled
- envelope and recipient status events enabled
- HMAC enabled with the same secret as `DOCUSIGN_WEBHOOK_SHARED_SECRET`

## Local development

For local development, use a public HTTPS tunnel and point DocuSign Connect at the tunnel URL instead of `localhost`.
