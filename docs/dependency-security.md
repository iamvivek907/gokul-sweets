# Next.js lint glob dependency

The frontend pins a scoped npm override for `@next/eslint-plugin-next`:
`fast-glob` resolves to the private local adapter in
`frontend/tools/next-lint-glob`, backed by pinned `tinyglobby@0.2.17`. Next.js and its ESLint
configuration remain at 16.3.8.

The plugin's only fast-glob call is `globSync(pattern, {onlyDirectories: true})`
in `dist/utils/get-root-dirs.js`. Tinyglobby provides that API and directory-only
behavior. The adapter explicitly sets `expandDirectories: false` to preserve
fast-glob matching (tinyglobby otherwise recursively expands directory matches).
It removes the micromatch → braces dependency chain. This removes
GHSA-vfj7-8cjw-p6xm rather than suppressing the security audit. Braces 3.0.3 has
no published patched version at the time of this change; npm's suggested
eslint-config-next 14 downgrade is incompatible with our current lint setup.

`tests/nextLintRootDiscovery.test.cjs` exercises real plugin discovery for
directory, brace, array and Windows-style patterns, and checks that ESLint
still reports invalid internal HTML links through a discovered Next project.
The adapter also preserves absolute-pattern results and removes directory
trailing slashes, matching the plugin's previous dependency.
CI continues to run `npm audit --audit-level=moderate` with all dependencies.

When updating the Next.js lint plugin, inspect its glob call sites again.
Remove this override once its dependency tree no longer includes the affected
braces package. It is scoped to this plugin; it is not a general fast-glob API
replacement for other packages.

Sources:
- https://github.com/advisories/GHSA-vfj7-8cjw-p6xm
- https://github.com/SuperchupuDev/tinyglobby
