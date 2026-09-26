# SCRUM-36 · MSG91 customer identity checkpoints

Owner chose MSG91 for customer OTP on 26 Sep 2026. The provider's OTP service
supports send, resend and verify; verify a provider-issued proof on the server
before creating any customer session. Do not trust a browser-supplied phone,
unverified access token, or a successful SMS send as proof of ownership.

The internal `OtpChallengePolicy` remains unused until a provider integration
needs locally generated codes. MSG91's provider-managed OTP flow must not be
combined with a second independent code verification without a clear protocol.

V56 and `VerifiedCustomerSessionStore` add a hashed, expiring, revocable bearer
token scoped to DEV or PROD. Issuance occurs only after server-side MSG91 proof
verification. Deliver the token in a Secure, HttpOnly, SameSite cookie, not a
URL or browser local storage. Keep the feature
flag OFF until SMS transport, verified session middleware, order ownership,
phone reassignment protection, per-phone/IP/device rate limits, resend/expiry,
provider outage recovery and user-facing checkout/account controls are tested.

Guest pickup remains available. Do not attach old orders or consent to a newly
verified phone without an explicit account recovery policy. No location or
marketing processing is enabled by these checkpoints.

`Msg91WidgetProofVerifier` now prepares the server-side access-token exchange
with a fixed MSG91 HTTPS origin. It refuses to call MSG91 while the identity
flag is OFF or the server authkey is missing, and rejects responses without an
explicit successful result and a verified Indian mobile in the provider data.
MSG91's exact live response shape must be checked with a DEV account before
activation; an unrecognised response fails closed. The widget token intended
for the browser and the server authkey are different credentials. The browser
widget is still pending.

V57 adds an environment-scoped verified phone registry. The internal
`VerifiedCustomerSubjectStore` creates a subject after
server-side proof verification and rejects malformed phones. The registry does
not grant access to older orders or consent: recycled numbers require an
explicit recovery and reassignment policy before any customer-facing identity
flow is enabled. Protect the registry as personal data under retention rules.

V60 changes subsequent verification of the same phone to rotate the subject
under a database transaction lock and revoke its previous sessions. Consent
and any future orders attached to the old subject are never inherited merely
because the number received another OTP. A restricted old-to-new audit trail
supports a later, separately verified account recovery process; it is not
queried by the customer login API. Re-verification signs out other devices,
including those owned by the same customer. Complete an explicit recovery
policy before enabling historical order access.

V58 and the internal `VerifiedIdentityExchange` verify MSG91 proof before a
database transaction that claims its digest, records the verified subject and
issues a session atomically. The digest is globally unique, including across
DEV and PROD. A repeated proof fails even if two instances race. Failed
issuance rolls back the claim. Activation still requires phone reassignment controls and a tested MSG91 DEV
response contract. Do not expose the issuance service directly to untrusted
callers: only the exchange calls it with a server-verified phone.

V59 adds shared, atomic limits to the internal exchange: five attempts per
server-observed source address per 15 minutes before provider verification,
and five per verified phone per hour before session issuance. HMAC digests
keep IP and phone values out of this table. Set a dedicated 32+ character
`GOKUL_IDENTITY_RATE_LIMIT_KEY` before enabling the identity flag; missing
configuration fails closed. Expired windows are purged hourly. A future HTTP
controller must pass a trusted server-derived source address, never a raw
browser header, and add device and provider-send limits before activation.

The customer identity controller now offers exchange, session status and
logout only when the OTP flag, environment isolation and strict environment
CORS switches are all ON. Mutating requests require HTTPS and the exact
configured storefront Origin; it uses the server-observed remote address for
limits. The response sets a Secure, HttpOnly, SameSite=Strict host-only cookie,
with no bearer in JSON, and does not attach orders or historic consent. Verify
trusted proxy TLS forwarding and browser credentials in DEV before turning
on this flag. The service remains OFF by default.

New pickup orders can receive an internal ownership record only when an
active verified cookie comes from an allowed HTTPS storefront Origin and its
current DEV/PROD subject has the same verified mobile as the checkout phone.
The insert happens during first order creation; an idempotency retry cannot
claim an existing guest order. A missing, expired or revoked cookie and a
different phone leave the order as a guest order. The record keeps its original
subject UUID when a phone is reverified, so a new holder cannot automatically
see the old holder's orders. Historical and guest orders are never adopted by
phone alone. Verified order listing and controlled recovery remain separate.

Behind a reverse proxy, set `GOKUL_IDENTITY_TRUSTED_PROXY_CIDRS` to the narrow
numeric CIDRs of the proxies directly connected to the API (comma separated).
The proxy must overwrite incoming `X-Forwarded-For` and `X-Forwarded-Proto`
and append its observed client IP and transport protocol; block direct access
to the API except through those proxies. The API walks the address chain from
the nearest trusted proxy to the first untrusted address for exchange limits,
and accepts HTTPS only when the nearest trusted proxy reports `https`. Missing
or invalid forwarding information fails closed. The default empty setting
ignores forwarding headers entirely and requires direct HTTPS at the API.
Do not trust a public CIDR or a wildcard range. Verify the exact proxy behavior
and address ranges in DEV before enabling identity; this configuration alone
does not complete the pending MSG91 callback and send-limit checks.

The optional profile and checkout entry point loads MSG91's Web SDK only after
the customer selects SMS verification. It requires the server readiness endpoint
and build-time `NEXT_PUBLIC_MSG91_WIDGET_ID` plus the browser-scoped
`NEXT_PUBLIC_MSG91_WIDGET_TOKEN`; never place `GOKUL_MSG91_SERVER_AUTHKEY` in
the frontend. The widget proof is sent with credentials to the backend exchange
and is never persisted in browser storage. Guest pickup remains available.
Verify the widget callback's exact proof shape and mobile behavior against a
DEV MSG91 account before enabling this flow. Provider send/resend limits and
phone reassignment controls remain mandatory for release.

Identity expiry, proof claims and rate-limit windows use UTC `Instant` values
and PostgreSQL `TIMESTAMP WITH TIME ZONE`; no server-local date is used for
authorization. A seven-day session remains valid across IST midnight and
expires at its exact instant even when the JVM uses a US timezone. Rate-limit
windows likewise do not reset at IST midnight. Hourly retention cleanup runs
in UTC. Display dates in Asia/Kolkata at the customer UI boundary; do not
change the server timezone to control identity expiry.

For business dates and legacy local timestamps across the app, see
`business-time-hosting.md`. They follow Asia/Kolkata even when the host OS
uses another timezone.
