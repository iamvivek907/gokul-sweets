# SCRUM-15: India time contract and historical timestamp audit

## Current contract

| Value | Meaning | Handling |
| --- | --- | --- |
| Pickup `LocalDate` | Store service calendar date in Asia/Kolkata | Compare as a date; do not shift with browser timezone. |
| Pickup `LocalTime` | Wall-clock opening/slot time in Asia/Kolkata | Display as a wall time, independent of the viewer's timezone or daylight saving. |
| Existing JSON `LocalDateTime` without offset | Legacy business timestamp, assumed Asia/Kolkata only for records written under the IST policy | Frontend parser attaches `+05:30` under `NEXT_PUBLIC_IST_TIME_FIX_ENABLED`; this assumption may be wrong for older rows. |
| Timestamp with `Z` or explicit offset | Absolute instant | Preserve instant, render in Asia/Kolkata for business UI. |
| Payment / reservation `LocalDateTime` in database | Currently an India wall-clock timestamp | Compare against India wall clock; payment verification must precede releasing a reservation. For future schema changes, use an explicit instant and version the conversion. |
| Campaign editor local input | India wall clock | Serialize with `+05:30` to an instant; render scheduled instants in Asia/Kolkata. |

`GOKUL_IST_TIME_FIX_ENABLED` controls backend legacy timestamps and order-number date; `NEXT_PUBLIC_IST_TIME_FIX_ENABLED` controls frontend legacy parsing and pickup-time display. Both default to true and should be changed together. Configuration definitions are in `EnhancementProperties`/`application.properties` and `frontend/lib/constants.ts`. The order-number suffix is random; the date prefix is the India business date, including UTC 18:30 year rollover.

## Historical rows: investigation before migration

Some older offset-less rows may have been written using the host timezone, while later rows use India time. An offset-less value does not reveal its origin. Do not blanket-shift a table by 5 hours 30 minutes; doing so corrupts genuine India-time records and can incorrectly expire a paid order.

1. Record deployment times, host `TZ`/JVM `user.timezone`, IST-toggle changes and schema changes from deployment logs. Include exact UTC instants for each change.
2. On a read-only production replica, sample `orders`, `payments`, `payment_webhook_events`, `pickup_slots` and campaign/notification rows around each deployment boundary, India midnight and UTC midnight. Select record IDs, `created_at`, `updated_at`, expiry, pickup service date, payment provider IDs and status; avoid exporting personal data.
3. Cross-check sample rows against payment provider event instants and trustworthy UTC request logs. Classify rows as proven UTC-host legacy, proven IST, or indeterminate. Treat any mixed-origin interval as indeterminate until corroborated.
4. Before an approved correction, create a backup and an explicit per-row classification manifest. Write a reversible migration restricted to proven rows, rehearse against a restored copy, verify statuses and expiration boundaries, and reconcile provider payments. Keep uncertain rows unchanged and flag them for manual handling.
5. Test 23:59 / 00:01 IST, 31 December / 1 January, a browser in a DST-observing timezone, an expiry racing with a payment callback, and toggles both enabled and disabled. Compare UTC and foreign browser results for the same India service date.

This change does not classify production rows or run a database migration: repository code cannot establish their historical origin. Notification scheduling has no new implementation in this story; future notification jobs must persist explicit instants and render planned delivery in Asia/Kolkata.
