# Frontend verification

`Build and verify` runs on PRs targeting `dev`/`main`, pushes to `dev`/`main`, and manual dispatch. Feature-branch pushes use the PR check instead of creating a duplicate run. New commits cancel obsolete runs for the same event/ref.

The `changes` job compares the complete PR range from its merge base, or the complete push range from `before` to `after`. Frontend, shared `tools/` dependencies, CI configuration and root Node/Vercel configuration trigger frontend verification. Backend-only and documentation-only ranges skip the build and browser jobs. Unknown history and manual dispatch run checks. A backend-only follow-up commit in a PR that already changes frontend still verifies the complete frontend PR change.

`frontend-build` runs dependency audit, lint, types, Node tests in two time zones and one production build. That exact build is shared with six isolated browser jobs. The suite manifest in `.github/scripts/browser-suites.json` retains all 51 existing unique scripts. The full single-checkout script runs all 28 scenarios; its former subset invocations were redundant.

Each browser group starts its own production server, prints individual script durations, and uploads separate screenshot/server-log evidence. No test assertions or scenarios are removed to improve runtime. Groups have a 20-minute deadline; individual scripts have a 10-minute deadline. Queue availability can still affect total time.

The final `frontend` job preserves the required check name. It succeeds only when change detection succeeded and either frontend inputs were unchanged, or the build and every browser group passed. Build failures, browser failures, cancellations and failed change detection fail this check.

To run a group locally after building and starting the production app on port 3311, provide the existing Playwright installation and run `node .github/scripts/run-browser-suite.mjs <group>` from the repository root. `BROWSER_BASE` can select another port. Do not shorten recovery or payment timeouts just to reduce CI duration.
