# SCRUM-49 · Privacy foundation for Sprint 3 delivery

Status: implementation design, not a shipped consent service. Based on `dev`
`b4277c37dbdd7bb7fbb7e591ff583ba437bc6733` (26 Sep 2026).

## Current boundary

The storefront supports guest pickup and stores checkout details in the browser.
The backend has customer contact records and staff-only customer administration,
but no customer-authenticated identity binding for consent across devices.
SCRUM-29 is explicitly blocked by this story. Do not silently attach a consent
record to an unverified phone number, browser storage key or staff account.

## Purpose and identity contract

| Purpose | Necessary for pickup? | Consent and storage |
| --- | --- | --- |
| Checkout contact and transactional order messages | Yes for an order | Explain the order purpose; store with the order under existing safeguards, independently of marketing choices. |
| Marketing and occasion reminders | No | Separate opt-ins with policy version, server time, withdrawal and verified customer identity before sending. Default false. |
| One-time device location for delivery check | No | Request only after a customer clicks **Use my location** on HTTPS; keep coordinates in memory for that check, discard after the response, and always provide manual locality entry. No background watch. |
| Coarse area analytics | No | Separate purpose and data minimization review; no raw coordinates or exact address in analytics events. |

## Implementation order

1. Define customer identity verification and session ownership first. A guest
   may use pickup without account, location or marketing opt-in. Do not infer
   authorization from a submitted phone number.
2. Introduce an append-only consent event with a verified subject, purpose,
   policy version, granted/withdrawn decision, server timestamp, channel and
   source environment. Maintain a current effective state per purpose with
   concurrency-safe updates. A withdrawal must prevent later optional sends.
3. Expose customer read/update endpoints bound to the verified subject; keep
   staff access restricted and audited. Separate DEV and PROD subjects and
   never copy optional consent from one environment to another.
4. Document retention/export/deletion with legal review before automation.
   Financial order records have a distinct retention basis; an account deletion
   request cannot silently erase required transaction evidence.
5. Add the SCRUM-29 manual locality and one-time location UI behind a default-OFF
   flag. Never treat approximate coordinates as address or delivery eligibility;
   SCRUM-30's server policy must confirm the full address before checkout.

## Verification gates

- A guest pickup completes with geolocation unavailable and no optional opt-in.
- A permission prompt occurs only on explicit click and a secure context;
  denial, timeout, revoked permission, inaccurate coordinates and offline mode
  all return to editable manual locality input.
- Concurrent grant/withdrawal resolves deterministically; withdrawal from one
  device blocks a subsequent optional send from another.
- A guessed phone number cannot read or change another person's preferences.
- No coordinate survives in logs, analytics, browser storage or the order; no
  location request fires when the flag is OFF.
- DEV and PROD data and consent are isolated; tests cover purpose separation,
  policy version, subject authorization, retention and audit.

The owner will manually deploy after Sprint 3. Keep SCRUM-49 and SCRUM-29 open
until code, CI, DEV flag ON/OFF evidence and owner QA are complete.

## Verified customer controls checkpoint

The profile offers separate optional choices only after a verified session.
`GET /api/customer/identity/consents` reads the effective ledger decisions and
shows older-policy grants as OFF until the customer explicitly grants the
current reviewed version. Any future sender must also require a current-policy
grant directly from the ledger at send time; the profile response is not an
authorization cache.
`PUT /api/customer/identity/consents/{purpose}` records a grant or withdrawal.
The backend resolves the subject from the secure session cookie; callers cannot
submit a phone or subject ID. Reads require a secure connection and allowed
Origin; mutations also require the trusted mutation check. Responses disable
caching. The current ledger is read again on account focus across devices.

Owner privacy: `GOKUL_FEATURES_CUSTOMER_CONSENT_CONTROLS=false` keeps these
controls inaccessible. ON requires `GOKUL_FEATURES_CUSTOMER_OTP_IDENTITY=true`,
the existing identity environment/CORS and provider abuse gates, and a
legally reviewed `GOKUL_CONSENT_POLICY_VERSION` (1–40 letters, digits, dots,
underscores or hyphens). Without these dependencies, requests return 404 and
the profile shows no controls. Order contact and guest pickup remain separate.
This checkpoint does not activate marketing, occasion sends or area analytics;
those send/event paths must read the current ledger choice before operating.
Retention/export/deletion and legal/privacy wording still need review; do not
enable the flag until the reviewed policy is available. No device coordinates
are requested or stored by these controls.

## Privacy request intake checkpoint

V64 stores verified, environment-scoped export and deletion-review requests.
The account page submits and shows received requests through the same secure,
allowed-Origin session checks as consent. Repeated submissions of the same
kind for a subject are idempotent, including retries after network failures.
The request row contains a subject UUID, environment, kind and server timestamp;
it contains no coordinates, marketing choice, phone or order data. The action
does not delete records or produce an export, and its UI says only "received".

The privacy owner must define a staffed triage and audited fulfillment process,
identity re-verification and subject-rotation handling, financial-record
retention basis, response period, and reviewed wording before enabling this
flag. A request from a rotated subject may need manual linkage via the existing
subject-rotation audit. Do not infer a retention duration or automate erasure.

## Owner review queue checkpoint

V65 adds `PRIVACY_REQUEST_VIEW` to the owner role only. The bounded
`GET /api/admin/privacy-requests?page=0` returns request metadata from the
configured environment and records the staff ID, environment, page and row
count in an audit event in the same transaction. A failed permission check
does not read or audit the queue. Neither phone numbers nor order contents are
included. The queue remains hidden when consent controls are OFF.

This is triage visibility, not a fulfillment action. No status change, actual
export, deletion, or financial-record retention rule is provided. Staff must
not claim completion from a received request. Before flag activation, the
privacy owner and legal reviewer still need an approved processing runbook,
recipient re-verification, rotation handling, retention schedule and wording.

## Triage checkpoint

V66 lets an authorized owner mark a request **In review** or **Needs
re-verification** from `/admin/privacy-requests`. Each state change is recorded
with the acting staff ID and prior/new state in an append-only event; concurrent
updates lock the request row. An idempotent retry does not duplicate an event.
The list remains environment-scoped and audited on read. The page shows
received timestamps in IST and exposes no completion or erase action. The
`PRIVACY_REQUEST_VIEW` permission is still owner-only. Triage does not contact
the customer or itself prove identity: the staffed re-verification and
fulfillment runbook still requires privacy/legal approval before release.

## Ledger checkpoint

V55 creates an append-only optional consent ledger keyed by a verified subject
UUID and explicit DEV/PROD environment. `ConsentLedger` defaults to denied,
serializes decisions for the same subject and purpose, and preserves withdrawal
history. There is intentionally no customer API or frontend opt-in yet: the
existing guest checkout has no verified customer session. SCRUM-36 must provide
verified ownership before a caller may record or read this ledger. This
checkpoint adds no permission prompt, analytics, marketing send or change to
pickup behaviour. It is not completion of SCRUM-49.
