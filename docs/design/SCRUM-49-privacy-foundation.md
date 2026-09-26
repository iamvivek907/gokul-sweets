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

## Ledger checkpoint

V55 creates an append-only optional consent ledger keyed by a verified subject
UUID and explicit DEV/PROD environment. `ConsentLedger` defaults to denied,
serializes decisions for the same subject and purpose, and preserves withdrawal
history. There is intentionally no customer API or frontend opt-in yet: the
existing guest checkout has no verified customer session. SCRUM-36 must provide
verified ownership before a caller may record or read this ledger. This
checkpoint adds no permission prompt, analytics, marketing send or change to
pickup behaviour. It is not completion of SCRUM-49.
