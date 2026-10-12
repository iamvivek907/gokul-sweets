# Per-story verification and handoff (SCRUM-17)

Every story starts from the current `dev` SHA, its Jira description and epic.
Create one focused PR to `dev`; move that story from To Do to In Progress when
the PR opens. Complete the PR template and keep its evidence current. The CI
workflow in `.github/workflows/verify.yml` runs on every branch push (including
the `dev` push made by merging a PR), every PR targeting `dev`, and manually.
The same tests therefore run on the branch commit and on the merged `dev`
commit. A branch with an open PR can have both push and PR runs. CI uses synthetic
credentials and an isolated PostgreSQL
service; it never exercises real payment or messaging accounts.

## Automated gates

| Gate | Command / coverage | Failure handling |
| --- | --- | --- |
| Frontend | `npm ci`, `npm run lint`, `npx tsc --noEmit`, Node tests in UTC and Los Angeles, `npm run build` with explicit HTTPS test origin | Fix failures before recommending merge; build-time test origin is not a DEV deployment. |
| Backend | Java 21, Gradle wrapper `./gradlew --no-daemon clean build bootJar` with isolated PostgreSQL 17 | `build` includes `check` (JUnit `test` plus `enhancementTest`) and packages the backend; explicit `bootJar` ensures the executable JAR. Investigate failures before merge. CI uses its own database and cannot assert production schema state. |
| Browser/provider integration | Manual DEV browser and provider tests; browser scripts in `frontend/tests/*.browser.mjs` require running API, seeded data and Playwright | Record exact setup, screenshots/API results and failure; do not describe unrun scripts as passed. |
| Migrations | Review every new Flyway migration for backwards compatible reads/writes and rollout order; verify Flyway version on DEV after deployment | Mismatch, failed migration or unreviewed irreversible data change blocks Done. |

For each new behaviour, keep the server-side enforcement authoritative. Give
the new switch a central documented OFF default, ON dependencies and safe OFF
path. Keep inventory enforcement active during rollback. Unit tests live next
to their backend domain package; add database/API tests for transactional or
authorization behaviour and browser tests for interaction/permission changes.
Test abuse cases such as OTP throttling, replayed callbacks and wrong branch
access when those features are affected. Test IST midnight and foreign browser
timezones for time-sensitive changes.

## DEV smoke after merge

1. Record the **merge commit from `dev`**, deployed backend/frontend SHA,
   deployment URL and time in Asia/Kolkata. Confirm the build embeds the DEV API
   origin and uses DEV CORS, database, R2 bucket and provider credentials.
2. Record effective flag states. Run one normal path with new flags OFF and a
   separate ON path after prerequisites are installed. For checkout changes,
   check cart, reservation, payment return, webhook reconciliation and order
   status. Replayed webhooks must not create duplicate orders or stock changes.
3. Inspect the Flyway version on the deployed backend and compare with the PR.
   Capture redacted screenshots or API request/response evidence; exclude
   tokens, OTPs, payment data and customer PII.
4. If a provider, device, branch staff flow or external account cannot be
   reached, label that check **unverified** and keep the Jira story in QA.

## Jira status and next-chat handoff

- On PR creation: In Progress. On user merge: In QA when DEV or manual testing
  remains; move to Done only after merge, CI, DEV deployment and affected ON/OFF
  smoke are verified. A merge alone is never evidence of deployment.
- A failed build, failed smoke, missing migration evidence or unavailable
  rollout switch leaves the story open and blocks enabling the feature.
- Add a final Jira comment using this structure so another session can fetch
  `dev`, read this story and its epic, and continue without relying on chat
  memory:

  ```text
  Story / PR / merge SHA:
  DEV frontend + backend deployment URLs, SHAs and IST deployment time:
  CI run URL and pass/fail (frontend, backend):
  Local test commands and results:
  Flag names, effective OFF/ON values and prerequisites:
  Flyway migration IDs and deployed version (or none):
  Redacted API/screenshots and manual smoke results:
  Negative/abuse/time cases exercised:
  Outstanding risks, owner and follow-up:
  Jira status and next dependent story key:
  ```

If deployment is outside the agent's access, document that limit and leave
the story in QA. Keep secrets out of PRs, Actions logs and Jira comments.
