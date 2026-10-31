# Lint glob compatibility

Older ESLint plugins call `minimatch` as a function; the patched 11.0.1 engine
exposes named exports. This adapter preserves the callable and named interfaces
used by this repository while delegating all matching to the patched engine.

The import plugin's legacy `makeRe(pattern).test(path)` interface delegates to
`Minimatch.match`. It does not recreate the regular-expression compiler removed
by the new engine. Other RegExp-specific interfaces are intentionally unsupported.
The engine is an explicit npm alias so audits still inspect its actual version.
