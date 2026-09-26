# SCRUM-36 · MSG91 customer identity checkpoints

Owner chose MSG91 for customer OTP on 26 Sep 2026. The provider's OTP service
supports send, resend and verify; verify a provider-issued proof on the server
before creating any customer session. Do not trust a browser-supplied phone,
unverified access token, or a successful SMS send as proof of ownership.

The internal `OtpChallengePolicy` remains unused until a provider integration
needs locally generated codes. MSG91's provider-managed OTP flow must not be
combined with a second independent code verification without a clear protocol.

V56 and `VerifiedCustomerSessionStore` add only a dormant, hashed, expiring,
revocable bearer token scoped to DEV or PROD. There is no issuance endpoint:
`issue` is for a future service that validates MSG91 proof server-side and
binds a stable verified customer subject. Deliver the token in a Secure,
HttpOnly, SameSite cookie, not a URL or browser local storage. Keep the feature
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
for the browser and the server authkey are different credentials. No browser
widget, login endpoint or bearer session cookie is exposed by this checkpoint.

V57 adds an environment-scoped verified phone registry. The internal
`VerifiedCustomerSubjectStore` atomically creates or retrieves the subject after
server-side proof verification and rejects malformed phones. The registry does
not grant access to older orders or consent: recycled numbers require an
explicit recovery and reassignment policy before any customer-facing identity
flow is enabled. Protect the registry as personal data under retention rules.

V58 and the internal `VerifiedIdentityExchange` verify MSG91 proof before a
database transaction that claims its digest, records the verified subject and
issues a session atomically. The digest is globally unique, including across
DEV and PROD. A repeated proof fails even if two instances race. Failed
issuance rolls back the claim. No public exchange endpoint or cookie exists;
activation still requires phone reassignment controls and a tested MSG91 DEV
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
