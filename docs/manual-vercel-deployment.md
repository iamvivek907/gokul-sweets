# Manual Vercel deployment

The Vercel project for this Next.js app should use `frontend` as its Root Directory.
Both `frontend/vercel.json` and the repository root `vercel.json` set
`git.deploymentEnabled` to `false` for every branch. This covers Vercel projects
configured with either Root Directory. Opening or updating a PR and merging to `dev`
should run GitHub Actions checks without creating Vercel Git deployments. The frontend
CI job checks both settings on every push and PR.

When you choose to deploy, use the Vercel CLI from `frontend` with the Vercel project
linked to that directory:

```sh
cd frontend
npx vercel link
npx vercel deploy --prod
```

The first command links the project once; choose the existing project, not a new one.
`npx vercel deploy` (without `--prod`) creates a manual preview deployment instead.
Before production deployment, select the intended Git commit and confirm that the
project's environment variables and domain are correct. GitHub Actions passing or a
PR merging does not mean the site has been deployed.

If a Vercel project linked to this repository has another Root Directory, place this
setting in its actual project root as well. Separately configured deploy hooks or
external workflows must also be disabled in their respective settings if present.
