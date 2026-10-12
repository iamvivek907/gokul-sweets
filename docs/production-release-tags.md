# Production release tags

After a promotion reaches `main`, `Tag verified production release` creates an
annotated tag and a GitHub Release for the current main commit once `Build and
verify` passes. When the printer workflow applies, it must pass too. Failed,
cancelled, unverified and superseded candidates are not tagged. This workflow
does not deploy frontend/backend services or certify real provider/device output.

The first stable release is `v1.0.0`. Subsequent releases increment the patch of
the highest stable semantic version, for example `v1.0.1`. A reviewed manual
minor or major version becomes the starting point for subsequent patch tags.
Unrelated and prerelease tags do not affect numbering. Rerunning a completed
verification workflow reuses the existing tag for that exact commit and repairs
a missing release entry rather than allocating another version.

Release notes contain the main SHA, verification link, generated changes and a
comparison with `dev`. Existing tags are never moved or deleted. A tag records a
source release, not which version is currently deployed; keep deployment IDs and
acceptance evidence in the deployment checklist.

## Plan the next promotion

Fetch current refs and tags, then use your selected release tag:

```sh
git fetch origin main dev --tags
git log --oneline v1.0.0..origin/dev
git rev-list --count v1.0.0..origin/dev
git diff --stat v1.0.0 origin/dev
```

Replace `v1.0.0` with the release you are comparing. Commit counts include merge
and maintenance commits; inspect PRs and diffs to estimate enhancement scope.
The Releases page lists all versions and the compare link shows development
since each release. Source parity at promotion is checked by comparing Git tree
IDs, not by assuming both branches have the same commit history.

If release creation fails, inspect the workflow permissions/error and rerun the
successful main verification workflow after correcting it. The release workflow
needs repository contents write and Actions read permissions. Never bypass a
failed verification simply to produce a tag.
