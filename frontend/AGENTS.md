<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->

# Customer theme for future stories

Read `../docs/design/customer-theme-contract.md` before changing customer-facing pages, navigation or checkout. Customer pages use `components/layout/AppShell.tsx`; keep the futuristic design scoped to `.future-storefront` and preserve the effective backend feature flags and flag-OFF appearance. Extend the shared header, branch control, drawer and bottom navigation rather than replacing them per story. Review desktop and mobile against the contract and run `node --test tests/customerThemeContract.test.cjs`.
