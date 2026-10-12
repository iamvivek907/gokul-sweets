# SCRUM-112 / SCRUM-90: customer checkout and kitchen planning

## Behavior

- Verified phone customers with no name use `GOKUL_GUEST` and their verified phone, and skip the contact screen. Guest customers still enter valid contact details.
- Pickup availability is rechecked on Continue. The review screen automatically previews the initial authoritative price, then requires explicit acceptance before reserving inventory. Offers stay on that review screen. Payment creation and final confirmation remain explicit and idempotent.
- Buttons show checking/opening states immediately; Next links display pending feedback. English / हिन्दी is saved on this browser and covers the ordering interface. Product names, business-authored descriptions, server error details and external payment-provider pages retain their original content.
- The browser's native Leave site prompt is removed from payment. Saved orders remain recoverable; intentional internal navigation retains the app's choice dialog.
- Kitchen status counts are buttons. The server pages branch-authorized live orders, with date/time workload cards and filters preserved in the URL. Start selected requires confirmation and reuses existing locked eligibility/KOT lifecycle checks; future orders cannot be started early.
- Enable kitchen sound once per admin session. The alarm lives across admin page navigation, checks every accessible branch every 15 seconds, and repeats chimes every 2 seconds while any order is eligible/overdue. Reading a notification does not stop it. Start all due orders in Preparing/KOT to stop it at the next successful refresh. Cancelled/ready orders no longer qualify. Multiple tabs coordinate chimes. Stale/offline checks pause sound rather than claim current state.
- Repeated staff push events are generated every configured interval until actual preparation begins. Replacement staff notifications renotify; customer notifications continue replacing quietly. Existing dispatch permission/status/environment checks remain authoritative.

## Rollout (checkout, Hindi and preparation board default ON)

Deploy frontend and backend together. The first three switches below default ON; explicit environment overrides still apply. Recurring locked-screen push reminders remain opt-in:

```
GOKUL_FEATURES_SIMPLIFIED_CHECKOUT=true
GOKUL_FEATURES_BILINGUAL_STOREFRONT=true
GOKUL_FEATURES_ADMIN_PREPARATION_BOARD=true
GOKUL_STAFF_RECURRING_PREPARATION_REMINDERS=true
GOKUL_STAFF_REPEAT_MINUTES=2
```

Simplified checkout benefits from existing accepted-checkout-quote, customer-identity, smart-pickup-selection and checkout-experience features. Existing staff alerts, scheduler, VAPID configuration and opted-in staff devices are prerequisites for push. Keep existing environment isolation/cross-environment recipient safeguards enabled. No deployment configuration is changed.

## Device limits

Web Audio requires a user gesture and an active browser/PWA. Reloading requires enabling sound again. A locked phone uses repeated OS push notifications, subject to installed-PWA support, notification permission, mute/DND and OS delivery. Neither continuous audio on a locked phone nor notifications on a powered-off phone can be guaranteed by a web app. Notification `requireInteraction` is advisory. For an uninterrupted locked-screen siren, a native managed-device application is required.

## Verification

- Frontend lint, type checking, production build and Node regressions in UTC / America/Los_Angeles.
- CI browser regression: verified blank-name contact, pickup -> review, read-only automatic quote, Hindi persistence, no mobile overflow; navigable kitchen filters, selected KOT confirmation, repeated alarm until preparation starts. Desktop 1280px / mobile 390px screenshots uploaded by CI.
- Backend integration regressions: branch authorization, grouping/paging, IST eligibility/overdue boundaries, flag-OFF behavior, repeated event deduplication and stopping when Preparing.
- Local Gradle/browser downloads are blocked by the workspace network; CI must pass before merge.
- Manual dev-device QA: real verified identity; guest checkout; price changes/add-ons and expired pickup; payment gateway return/close/reload; real Hindi-speaking staff usability; two staff tabs; Android installed PWA/iOS home-screen notifications; lock/DND/offline tests; preparation/KOT printer failure and concurrent staff actions. Real payment, push delivery and printer behavior are not proven by synthetic browser fixtures.

PR #151 follow-up: simplified checkout and the staff preparation board now default ON (explicit environment overrides still apply). The language control is always visible and retains the saved choice independently of rollout flags. Verified contact autofill runs in both pickup experiences even when simplified checkout is explicitly disabled. Deploy frontend and backend together.
