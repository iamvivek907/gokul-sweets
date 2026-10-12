# SCRUM-105 branch experience rollout

Baseline: `dev` at `b2d2c08c1f4f9f471f78e71a84286ab9314f4027` (includes SCRUM-37 PR #120).

## Configuration and ownership

`GOKUL_FEATURES_BRANCH_EXPERIENCE=false` by default. Branch operations own published branch cover and descriptions. The flag requires Flyway V79, configured R2, and staff with `BRANCH_MANAGE`. On OFF, customer branch cards and menu navigation use the existing paths. The backend ignores unpublished drafts in public responses. On ON, the public branch DTO includes published desktop/mobile cover, alt text, description, and pickup eligibility from `branch_pickup_settings.enabled`. Nothing here changes checkout, inventory or payment commitments.

Admin → Branches presents draft and published previews, image upload through the existing validated R2 storage service, copy, explicit publish, revision history and restore. Every edit needs the previous version (`If-Match`); stale changes return 409. Every mutation checks `BRANCH_MANAGE` and branch scope and records actor, before/after and revision. Upload accepts static JPEG/PNG/WebP up to 5 MB, as in the campaign media path. A failed upload leaves published content intact; older objects remain available for rollback. Clean up unreferenced objects through the existing media maintenance process after the retention decision.

The branch card opens `/branches/{id}` when ON. The detail page shows published branch media and public branch information. Its pickup action reuses the cart-safe branch switch control. The menu's Branch details tab opens the same branch. Missing media has a neutral fallback.

Pickup is governed by existing branch pickup settings and live slot checks at checkout. Table booking, occasion booking and banquet booking are not implemented. The admin explicitly says those services cannot yet be published, and the public page never advertises an actionable booking CTA. Add actual branch policy and end-to-end booking routes in the related P4/P6 stories before enabling those controls.

## DEV QA

1. Deploy backend with V79, then frontend. Confirm public flag OFF retains existing cards/menu link, and the new API does not publish drafts.
2. As authorized staff scoped to branch A, upload distinct desktop/mobile covers and alt text for A. Save draft; confirm the public card still shows the previous image. Publish; confirm A's card and `/branches/A` change, B stays untouched. Repeat for B.
3. Check failed/oversized or disguised media, missing alt text, stale `If-Match`, out-of-scope staff and unauthorized API calls. They must not change public content.
4. Restore an older revision; verify a new publication revision and both customer surfaces. Check mobile/desktop, broken image fallback, keyboard and reduced motion.
5. Disable pickup in branch settings; details must not offer the pickup action. Re-enable it and confirm a fresh checkout still enforces live slots and inventory.

CI backend DB/API integration test and real DEV R2/staff/device tests are required before Done. The repo cannot attest DEV deployment automatically.
