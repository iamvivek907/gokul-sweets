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
server-side proof verification and rejects malformed phones. The owner selected
phone-OTP-only recovery of previously verified orders. A recycled number can
therefore expose those orders to its new holder. Guest orders and old consent
are excluded. Protect the registry and transfer audit as personal data.

V60 changes subsequent verification of the same phone to rotate the subject
under a database transaction lock and revoke its previous sessions. Consent
is never inherited merely because the number received another OTP. When
`GOKUL_IDENTITY_PHONE_ONLY_ORDER_RECOVERY=true`, V63 applies the owner-selected
policy: a fresh provider-verified OTP transfers all prior verified order
ownership for that phone to the new subject, even when the prior session is
lost. It defaults OFF, retaining the prior-session-plus-OTP continuity rule.
The transfer and per-order audit are atomic; the
rotation audit records the count. Guest orders and consent do not transfer.
Re-verification signs out other devices. A new holder of a recycled number
can recover the old holder's verified orders; the owner selected this tradeoff
on 27 Sep 2026.

V58 and the internal `VerifiedIdentityExchange` verify MSG91 proof before a
database transaction that claims its digest, records the verified subject and
issues a session atomically. The digest is globally unique, including across
DEV and PROD. A repeated proof fails even if two instances race. Failed
issuance rolls back the claim. Activation still requires a tested MSG91 DEV
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
different phone leave the order as a guest order. Guest orders are never adopted
by phone alone. Authenticated `/api/customer/identity/orders` and its order detail
route read only records belonging to the current environment and exact session
subject, returning 401 without a session and 404 for a foreign order. My Orders
combines that list with the browser's existing guest order numbers without
clearing cart or local history. With phone-only recovery enabled,
reverification rotates the subject and transfers verified orders to the new
session after phone OTP. The new holder of a
reassigned phone can see those orders; OTP does not prove the original
purchaser's identity.

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
browser context may not have the original session. Available rebate lookup,
apply and removal, and customer review context and submission, use the same
verified-order guard and send credentials. Public product rating summaries
remain available for menu browsing.
Guest order-number capability routes still require a separate authorization
audit before treating an order number as sufficient proof of ownership.

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
the privacy impact of the chosen phone-only recovery policy must be reviewed
before release.

Identity expiry, proof claims and rate-limit windows use UTC `Instant` values
and PostgreSQL `TIMESTAMP WITH TIME ZONE`; no server-local date is used for
authorization. A 30-day remembered session remains valid across IST midnight and
expires at its exact instant even when the JVM uses a US timezone. Rate-limit
windows likewise do not reset at IST midnight. Hourly retention cleanup runs
in UTC. Display dates in Asia/Kolkata at the customer UI boundary; do not
change the server timezone to control identity expiry.

For business dates and legacy local timestamps across the app, see
`business-time-hosting.md`. They follow Asia/Kolkata even when the host OS
uses another timezone.
