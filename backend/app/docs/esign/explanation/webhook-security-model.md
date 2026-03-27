# Explanation: Webhook Security Model

The webhook endpoint is reachable without application login because DocuSign Connect cannot authenticate with the app's normal user session or basic-auth flow.

That does not mean the endpoint is open. The app verifies each webhook request by computing an HMAC-SHA256 over the raw request body and comparing it with the signature from `X-DocuSign-Signature-1`.

This is the right model for this integration because:

- it authenticates the sender at the message level instead of trusting network location
- it protects the exact payload bytes that were delivered
- it works for local tunnels, dev environments, and production the same way

The Spring Security exception only allows the request through to the controller. The controller still rejects unsigned or incorrectly signed payloads with `401`.
