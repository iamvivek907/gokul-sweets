# Backend documentation, formatting and method diagnostics

This maintenance pass preserves existing business statements, SQL values, API contracts,
annotations, transaction boundaries and exception types. It adds documentation and timing,
formats source files, and extracts named compile-time constants without changing their values.

## Formatting and documentation

Run `cd backend && ./gradlew formatJava` before committing Java changes. `./gradlew check`
runs `checkJavaFormat` and `checkMethodTimingCoverage` alongside the existing regression suites.
The coverage check rejects placeholder method descriptions, requires JavaDoc for types/methods,
and validates timing labels against the actual declaring-type path and parameter signature.
Verifier regressions cover copied labels, overloads, generics, varargs and anonymous callbacks.
It requires a matching start/finally-finish
wrapper for every explicit concrete business method; the diagnostics package is excluded
from instrumentation to prevent logging recursion. The pinned Google Java Format
AOSP style uses four-space indentation, separates declarations, and wraps long expressions.
It applies to maintained main, test and enhancement Java sources and verification tooling.

Existing meaningful JavaDoc is retained. Placeholder descriptions have been replaced with
endpoint contracts, authorization checks, database effects, return expressions and rejection
reasons grounded in each method. Security and cleanup methods document their MFA/replay,
revision, lease and retention rules explicitly. Previously undocumented types and explicitly declared methods
have summaries and parameter/return/declared-exception tags. Record components, exposed enum
values and explicit constructors are documented too. Standard Javadoc does not expand Lombok
and reports missing synthetic/default-constructor comments; a published API documentation
workflow should delombok generated members first. This change documents maintained source. Update those contracts when behavior
changes; document business invariants and unusual concurrency rules rather than narrating each line.

## Method timing and diagnostic logging

Timing wraps 2,079 existing, explicitly declared methods in `try/finally`, including private,
static, recursive and self-invoked methods. It does not add AOP proxies or reorder transaction
advice. Constructors, abstract/native declarations, lambda bodies and Lombok-generated methods
are not independently instrumented; their work is included in an enclosing measured method.
Spring transaction commit/rollback performed outside the method body is outside that method's
reported duration. Completion events also occur when a method throws and do not imply success.

Defaults:

| Environment variable | Default | Effect |
| --- | --- | --- |
| `GOKUL_METHOD_TIMING_ENABLED` | `true` | Enable elapsed-time diagnostics after application configuration initializes. |
| `GOKUL_METHOD_TIMING_SLOW_THRESHOLD_MS` | `1000` | Emit WARN when a method reaches this inclusive elapsed duration. |
| `GOKUL_METHOD_TIMING_LOG_LEVEL` | `INFO` | Slow-method WARN events remain visible; per-method completion events are suppressed. |

Set the log level to `DEBUG` for every measured completion; `TRACE` also records entry.
Use these levels during investigation, then restore `INFO` to avoid excessive output.
Disable timing with `GOKUL_METHOD_TIMING_ENABLED=false`; the wrappers return immediately.
No arguments, return values, exception contents, OTPs, tokens, customer details or payment payloads
are added to logs. Existing application logs retain their current behavior.

Example (illustrative duration):

```text
method_slow class=com.gokulsweets.restaurant.order.service.OrderService method=createOrder(CreateOrderRequest) durationMs=1240.3
```

Times are inclusive wall-clock durations from `System.nanoTime`, not CPU usage. Parent and child
measurements overlap: do not add them together. A long duration can include database waits,
locks, external providers or deliberate waiting. Identify the method, correlate with existing
operation/provider logs, and investigate the underlying work before optimizing.
Timing adds two helper calls per method and a monotonic-clock read when enabled. DEBUG/TRACE
create substantially more log traffic. Logger RuntimeExceptions are caught so diagnostics do not
replace the business return value or exception. Early startup before timing configuration is
created does not emit measurements.

## Package constants

64 existing named primitive/String compile-time values are centralized in 24 package-local
`AppConstant.java` files. Existing fields retain aliases, including public fields, so callers
and tests remain compatible. Constants retain their original types and exact values.

This deliberately keeps runtime object initializers (maps, sets, formatters, time zones),
annotation/configuration defaults, structural indexes/zero/one values, and inline SQL/message
literals in their existing context. Moving every literal would obscure intent and can change
initialization or type-inference behavior. Extract a new meaningful constant when introducing
a shared policy/limit; avoid using one mutable global constants collection.

## Preservation evidence

Run from `backend`:

```sh
./gradlew verifyBehaviorPreservation -PmaintenanceBaseRevision=53b2e9424cb1ef25b791910b8569527da0e8ca2b
```

The JavaParser-based check compares 817 existing Java files with the baseline. It strips only
verified timing wrappers and documentation, resolves package constant aliases, and normalizes
formatter-only compile-time string splitting. It checks existing annotations, declarations and
executable method syntax; SQL/text-block contents are compared as cooked strings. Tests receive
formatting only. Newly introduced diagnostics have separate regression coverage.

The preservation check is a one-time PR safeguard, not a permanent ban on future functional
changes. CI runs it for this maintenance branch against the PR base. Normal formatter and test
checks remain after merging. Compilation and database-backed tests complement this source-level
check; syntax equivalence alone does not quantify instrumentation overhead.
