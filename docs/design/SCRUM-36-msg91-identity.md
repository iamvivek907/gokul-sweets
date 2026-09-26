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
flag OFF until SMS delivery, provider send/resend and expiry controls, proxy
behavior, phone reassignment and customer checkout/account flows are verified
in DEV. The backend cannot enforce send/resend limits inside MSG91's browser
widget; its own gate limits opening the widget, but a caller can contact MSG91
directly using the browser widget token. Configure vendor-side controls.

Guest pickup remains available. Do not attach old orders or consent to a newly
verified phone without an explicit account recovery policy. No location or
marketing processing is enabled by these checkpoints.

`Msg91WidgetProofVerifier` performs the server-side access-token exchange
with a fixed MSG91 HTTPS origin. It refuses to call MSG91 while the identity
flag is OFF or the server authkey is missing, and rejects responses without an
explicit successful result and a verified Indian mobile in the provider data.
MSG91's exact live response shape must be checked with a DEV account before
activation; an unrecognised response fails closed. The widget token intended
for the browser and the server authkey are different credentials.

V57 adds an environment-scoped verified phone registry. The internal
`VerifiedCustomerSubjectStore` creates a subject after
server-side proof verification and rejects malformed phones. The registry does
not grant access to older orders or consent: recycled numbers require an
explicit recovery and reassignment policy before any customer-facing identity
flow is enabled. Protect the registry as personal data under retention rules.

V60 changes subsequent verification of the same phone to rotate the subject
under a database transaction lock and revoke its previous sessions. Consent
is never inherited merely because the number received another OTP. Orders
transfer only if the exchange also presents a still-live old session for the
same subject and newly verified OTP for the same phone in one transaction.
A phone-only verification never transfers them. A restricted old-to-new audit trail
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
configuration fails closed. Expired windows are purged hourly. The controller
passes a trusted server-derived source address, never a raw browser header.
Widget start is limited to five attempts per IP in 15 minutes and per
server-minted device in one hour; proof exchange has independent device and
source limits. V62 stores only a digest of the 30-day device token and expires
it in UTC. These limits do not replace vendor-side SMS send/resend controls.

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
subject UUID when a phone is reverified without a live old session, so a new
holder cannot automatically see the old holder's orders. Guest orders are never adopted by
phone alone. Authenticated `/api/customer/identity/orders` and its order detail
route read only records belonging to the current environment and exact session
subject, returning 401 without a session and 404 for a foreign order. My Orders
combines that list with the browser's existing guest order numbers without
clearing cart or local history. Reverification rotates the subject; older
verified orders follow a new OTP only when the browser has the live prior
session. Cross-device recovery after losing that session requires an owner-
approved policy proving more than phone possession. The current view is not
complete account recovery.

The flag owner must verify MSG91's widget send/resend thresholds, cooldown,
expiry, token response and mobile flows in DEV. Only then set
`GOKUL_IDENTITY_PROVIDER_ABUSE_CONTROLS_VERIFIED=true` alongside the server
authkey, dedicated rate-limit key, strict CORS, environment isolation and
narrow trusted proxy CIDRs. The new guard defaults OFF even if the main OTP
flag is accidentally enabled. Secure cookies must reach the API; when frontend
and API use different sites, a SameSite=Strict cookie will not be sent. Use a
same-site API origin and confirm browser credential behavior before enabling.

`GOKUL_IDENTITY_PROTECT_LEGACY_ROUTES` is a separate default-OFF rollout guard.
When both identity and this guard are enabled, order-number lookups, history,
pending checkout updates, payment initiation, payment lookup and payment-ID
refresh/verification require the current verified subject for orders already
bound to a subject. A missing or wrong session receives 404, while orders that
were placed as guests retain the order-number capability flow. Checkout sends
the secure cookie with these requests. Validate PhonePe and Razorpay return
flows in DEV before enabling this guard: an external payment return in a new
browser context may not have the original session. Additional order-bound
offers/rebates and guest capability routes need a separate authorization audit.

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
