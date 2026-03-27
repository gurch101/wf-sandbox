# How-to: Run DocuSign Locally

This guide shows how to test DocuSign e-sign flows and Connect webhooks from a developer machine.

## Prerequisites

- A DocuSign developer account
- Local app running, for example on `http://localhost:8080`
- A public HTTPS tunnel such as `ngrok`
- `DOCUSIGN_ACCOUNT_ID`
- `DOCUSIGN_ACCESS_TOKEN`
- `DOCUSIGN_WEBHOOK_SHARED_SECRET`

## Start the app

Set the DocuSign environment variables:

```bash
export DOCUSIGN_BASE_PATH=https://demo.docusign.net/restapi
export DOCUSIGN_ACCOUNT_ID=your-account-id
export DOCUSIGN_ACCESS_TOKEN=your-access-token
export DOCUSIGN_RETURN_URL=https://your-ui.example.com/docusign/return
export DOCUSIGN_WEBHOOK_SHARED_SECRET=replace-with-random-secret
```

Run the backend.

## Start a tunnel

Expose the local port with a public HTTPS URL:

```bash
ngrok http 8080
```

Take the generated public URL and keep it for the next steps.

Example:

```text
https://abc123.ngrok-free.app
```

## Configure DocuSign Connect

Set the Connect webhook URL to:

```text
https://abc123.ngrok-free.app/api/esign/envelopes/webhooks/docusign
```

Configure the webhook to send JSON payloads.

Configure the HMAC secret in DocuSign Connect to exactly match `DOCUSIGN_WEBHOOK_SHARED_SECRET`.

The app verifies the `X-DocuSign-Signature-1` HMAC header against the raw request body.

## Create and complete an envelope

1. Create an envelope through the app.
2. Complete signing in DocuSign.
3. Watch the local app logs and confirm the webhook is received.
4. Verify `esign_envelopes.status`, `esign_envelopes.completed_at`, and the signer rows update.

## Test in-person embedded signing

1. Create an in-person envelope through the app.
2. Request a fresh embedded view URL from:

```text
POST /api/esign/envelopes/{id}/signers/{roleKey}/embedded-view
```

3. Open the returned `signingUrl`.
4. Complete signing and confirm the webhook updates local status.

## Troubleshooting

If DocuSign cannot reach the webhook:

- verify the tunnel URL is still active
- verify the URL is HTTPS
- verify the Connect configuration points to the current tunnel URL

If the app returns `401` on the webhook:

- verify the HMAC secret matches on both sides
- verify the webhook is sending `X-DocuSign-Signature-1`

If the app returns `503` on the webhook:

- verify `DOCUSIGN_WEBHOOK_SHARED_SECRET` is set for the running app
