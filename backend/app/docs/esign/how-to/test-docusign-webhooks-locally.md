# How-To: Test DocuSign Webhooks Locally

## Goal

Run the app on a developer machine, expose the webhook endpoint with a public HTTPS tunnel, and verify that DocuSign webhook events update `esign_envelopes` and `esign_signers`.

## Prerequisites

- A DocuSign developer account
- A valid DocuSign access token and account ID
- The app running locally
- A public HTTPS tunnel such as `ngrok` or `cloudflared`

## 1. Start the app with DocuSign config

Set these environment variables before starting `backend/app`:

```bash
export DOCUSIGN_BASE_PATH="https://demo.docusign.net/restapi"
export DOCUSIGN_ACCOUNT_ID="YOUR_ACCOUNT_ID"
export DOCUSIGN_ACCESS_TOKEN="YOUR_ACCESS_TOKEN"
export DOCUSIGN_RETURN_URL="https://your-frontend.example.com/esign/return"
export DOCUSIGN_WEBHOOK_HMAC_SECRET="replace-with-a-long-random-secret"
export DOCUSIGN_HOST_EMAIL="system@sandbox.local"
export DOCUSIGN_HOST_NAME="Sandbox Host"
```

Start the app on a reachable local port, for example:

```bash
./gradlew :app:bootRun
```

## 2. Expose the local app over HTTPS

Example with `ngrok`:

```bash
ngrok http 8080
```

Assume `ngrok` gives you:

```text
https://abc123.ngrok-free.app
```

Your DocuSign webhook URL becomes:

```text
https://abc123.ngrok-free.app/api/esign/envelopes/webhooks/docusign
```

`localhost` alone will not work because DocuSign cannot reach your machine directly.

## 3. Configure DocuSign Connect

In DocuSign Connect, configure a listener that posts to:

```text
https://abc123.ngrok-free.app/api/esign/envelopes/webhooks/docusign
```

Enable HMAC signing and use the same secret value as `DOCUSIGN_WEBHOOK_HMAC_SECRET`.

The app expects the DocuSign HMAC signature in the standard header:

```text
X-DocuSign-Signature-1
```

## 4. Create and complete an envelope

Call the app create endpoint with:

- a PDF containing hidden anchors such as `/s1/` or `/d1/`
- signer details for the DocuSign flow you want to test

When the signer completes the signing flow:

- DocuSign posts a webhook event to the tunnel URL
- the app verifies the HMAC against the raw request body
- the app updates `esign_envelopes.status`
- the app updates `esign_signers.status`, `viewed_at`, and `completed_at`

## 5. Verify state in the app

Query the envelope status endpoint:

```text
GET /api/esign/envelopes/{id}
```

For embedded signing, request a fresh signing session URL from:

```text
POST /api/esign/envelopes/{id}/signers/{roleKey}/embedded-view
```

After completion, the app should also expose:

- `GET /api/esign/envelopes/{id}/certificate`
- `GET /api/esign/envelopes/{id}/signed-document`

## Troubleshooting

- `401` on the webhook endpoint usually means the HMAC secret in DocuSign does not match `DOCUSIGN_WEBHOOK_HMAC_SECRET`.
- No webhook traffic usually means the tunnel URL is wrong, expired, or not publicly reachable.
- Stale signer or envelope statuses usually means Connect is misconfigured or not sending recipient-level updates.
