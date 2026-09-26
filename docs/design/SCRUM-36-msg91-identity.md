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
