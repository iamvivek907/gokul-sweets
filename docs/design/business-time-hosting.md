# Business time when hosting outside India

Gokul Sweets operates on the `Asia/Kolkata` calendar regardless of the server
region or the customer's device timezone. The Java application sets its JVM
default zone to `Asia/Kolkata` before Spring, JDBC and scheduled jobs start so
legacy `LocalDateTime.now()` callbacks follow business time. New code should
use `ApplicationClock.BUSINESS_ZONE` explicitly for pickup dates, slot windows,
order numbers and reports; scheduled daily business jobs must declare that
zone. A host's operating-system timezone is not part of the business contract.

An expiry is an elapsed-time deadline, not an India calendar date. Identity
sessions, consumed proofs and rate limits use `Instant` and PostgreSQL
`TIMESTAMP WITH TIME ZONE`. Those instants remain the same on hosts in any
timezone; India display converts them at the UI boundary. The identity cleanup
cron explicitly uses UTC because it is hourly maintenance, not a business-day
event.

The storefront formats pickup wall times and Java `LocalDateTime` responses
through `frontend/lib/businessTime.ts`. Calendar additions start with the India
date rather than the device's local date. CI runs frontend regressions under
both UTC and America/Los_Angeles; identity integration tests also cross India
midnight while the JVM default is America/Los_Angeles.
